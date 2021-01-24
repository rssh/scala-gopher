package gopher

import cps._
import gopher.impl._
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import scala.concurrent.duration.Duration

trait ReadChannel[F[_], A]:

   thisReadChannel =>

   type read = A

   def gopherApi: Gopher[F]

   def asyncMonad: CpsSchedulingMonad[F] = gopherApi.asyncMonad

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

   def aforeach_async(f: A=>F[Unit]): F[F[Unit]] =
      rAsyncMonad.pure(foreach_async(f))

   def aforeach(f: A=> Unit): F[Unit] =
      foreach_async( x => rAsyncMonad.pure(f(x)))   

   /**
   * run code each time when new object is arriced.
   * until end of stream is not reached
   **/  
   inline def foreach(f: A=>Unit): Unit = 
      await(aforeach(f))(using rAsyncMonad)

   def map[B](f: A=>B): ReadChannel[F,B] =
      new MappedReadChannel(this, f)
   
   def mapAsync[B](f: A=>F[B]): ReadChannel[F,B] =
      new MappedAsyncReadChannel(this, f)

   def dup(bufSize: Int=1, expiration: Duration=Duration.Inf): (ReadChannel[F,A], ReadChannel[F,A]) =
      DuppedInput(this, bufSize)(using gopherApi).pair

   def afold[S](s0:S)(f: (S,A)=>S): F[S] =
      fold_async(s0)((s,e) => asyncMonad.pure(f(s,e)))

   def fold_async[S](s0:S)(f: (S,A) => F[S] ): F[S] =
      given CpsSchedulingMonad[F] = asyncMonad
      async[F] {
         var s = s0
         while{
           optRead match
             case Some(a) => 
                  s = await(f(s,a))
                  true
             case None =>
                  false
         }do()
         s
      }
   
   inline def fold[S](s0:S)(f: (S,A) => S ): S =
      await[F,S](afold(s0)(f))(using rAsyncMonad)   

   class DoneReadChannel extends ReadChannel[F,Unit]:

      def addReader(reader: Reader[Unit]): Unit =
         thisReadChannel.addDoneReader(reader)

      def addDoneReader(reader: Reader[Unit]): Unit =
         thisReadChannel.addDoneReader(reader)

      def gopherApi: Gopher[F] = thisReadChannel.gopherApi  

   end DoneReadChannel


   class SimpleReader(f: Try[A] => Unit) extends Reader[A]:

      def canExpire: Boolean = false
      def isExpired: Boolean = false

      def capture(): Option[Try[A]=>Unit] = Some(f)

      def markUsed(): Unit = ()
      def markFree(): Unit = ()

   end SimpleReader

end ReadChannel




