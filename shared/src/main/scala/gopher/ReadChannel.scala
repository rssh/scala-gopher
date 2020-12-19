package gopher

import cps._
import gopher.impl._
import scala.util.Try
import scala.util.Success
import scala.util.Failure

trait ReadChannel[F[_], A]:

   thisReadChannel =>

   type read = A

   protected def gopherApi: Gopher[F]

   protected def asyncMonad: CpsSchedulingMonad[F] = gopherApi.asyncMonad

   // workarround for https://github.com/lampepfl/dotty/issues/10477
   protected def rAsyncMonad: CpsAsyncMonad[F] = asyncMonad

   def addReader(reader: Reader[A]): Unit

   def addDoneReader(reader: Reader[Unit]): Unit 

   lazy val done: ReadChannel[F,Unit] = DoneReadChannel()

   type done = Unit

   /**
    * async version of read. Immediatly return future, which will contains result of read or failur with StreamClosedException
    * in case of stream is closed.
    */
   def aread:F[A] = 
      asyncMonad.adoptCallbackStyle(f => addReader(SimpleReader(f)))
                               
   inline def read: A = await(aread)(using rAsyncMonad)

   inline def ? : A = await(aread)(using rAsyncMonad)

  /**
   * return F which contains sequence from first `n` elements.
   */
   def atake(n: Int): F[IndexedSeq[A]] = 
      given CpsAsyncMonad[F] = asyncMonad
      async[F]{
         var b = IndexedSeq.newBuilder[A]
         try {
            var c = 0
            while(c < n) {
               val a = read
               b.addOne(a)
               c = c + 1
            }
         }catch{
            case ex: ChannelClosedException =>
         }
         b.result()
      }   

   def aOptRead: F[Option[A]] =
       asyncMonad.adoptCallbackStyle( f =>
                   addReader(SimpleReader{ x => x match
                                            case Failure(ex: ChannelClosedException) => f(Success(None)) 
                                            case Failure(ex) => f(Failure(ex)) 
                                            case Success(v) => f(Success(Some(v))) 
                                         })
       )

   inline def optRead: Option[A] = await(aOptRead)(using rAsyncMonad)

   def foreach_async(f: A=>F[Unit]): F[Unit] =
      given CpsAsyncMonad[F] = asyncMonad
      async[F]{
         var done = false
         while(!done) {
            optRead match
               case Some(v) => await(f(v))
               case None => done = true
         }
      }

   /**
   * run code each time when new object is arriced.
   * until end of stream is not reached
   **/  
   inline def foreach(f: A=>Unit): Unit = 
      await(foreach_async( x => rAsyncMonad.pure(f(x)) ))(using rAsyncMonad)
 
   def dup(): (ReadChannel[F,A], ReadChannel[F,A]) =
      DuppedInput(this)(using gopherApi).pair

   class DoneReadChannel extends ReadChannel[F,Unit]:

      def addReader(reader: Reader[Unit]): Unit =
         thisReadChannel.addDoneReader(reader)

      def addDoneReader(reader: Reader[Unit]): Unit =
         thisReadChannel.addDoneReader(reader)

      protected def gopherApi: Gopher[F] = thisReadChannel.gopherApi  

   end DoneReadChannel


   class SimpleReader(f: Try[A] => Unit) extends Reader[A]:

      def canExpire: Boolean = false
      def isExpired: Boolean = false

      def capture(): Option[Try[A]=>Unit] = Some(f)

      def markUsed(): Unit = ()
      def markFree(): Unit = ()

   end SimpleReader

end ReadChannel



