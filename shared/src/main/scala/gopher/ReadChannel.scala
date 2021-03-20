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
                               
   transparent inline def read: A = await(aread)(using rAsyncMonad)

   transparent inline def ? : A = await(aread)(using rAsyncMonad)

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

   transparent inline def optRead: Option[A] = await(aOptRead)(using rAsyncMonad)

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
   transparent inline def foreach(inline f: A=>Unit): Unit = 
      await(aforeach(f))(using rAsyncMonad)

   def map[B](f: A=>B): ReadChannel[F,B] =
      new MappedReadChannel(this, f)
   
   def mapAsync[B](f: A=>F[B]): ReadChannel[F,B] =
      new MappedAsyncReadChannel(this, f)

   def filter(p: A=>Boolean): ReadChannel[F,A] = 
      new FilteredReadChannel(this,p)

   def filterAsync(p: A=>F[Boolean]): ReadChannel[F,A] = 
      new FilteredAsyncReadChannel(this,p)

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
   
   transparent inline def fold[S](inline s0:S)(inline f: (S,A) => S ): S =
      await[F,S](afold(s0)(f))(using rAsyncMonad)   
   
   def zip[B](x: ReadChannel[F,B]): ReadChannel[F,(A,B)] = 
      given CpsSchedulingMonad[F] = asyncMonad
      val retval = gopherApi.makeChannel[(A,B)]()
      asyncMonad.spawn(async[F]{
         var done = false
         while(!done) {
            this.optRead match
               case Some(a) =>
                  x.optRead match
                     case Some(b) =>
                        retval.write((a,b))
                     case None =>
                        done=true
               case None =>
                  done = true
         }
         retval.close()
      })
      retval

   def or(other: ReadChannel[F,A]):ReadChannel[F,A] = 
      new OrReadChannel(this, other)

   def |(other: ReadChannel[F,A]):ReadChannel[F,A] =
      new OrReadChannel(this,other)   

   def append(other: ReadChannel[F,A]): ReadChannel[F, A] =
      new AppendReadChannel(this, other)

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

object ReadChannel:

   def fromIterable[F[_],A](c: IterableOnce[A])(using Gopher[F]): ReadChannel[F,A] =
      given asyncMonad: CpsSchedulingMonad[F] = summon[Gopher[F]].asyncMonad
      val retval = makeChannel[A]()
      asyncMonad.spawn(async{
         val it = c.iterator
         while(it.hasNext) {
            val a = it.next()
            retval.write(a) 
         }
         retval.close()
      })
      retval


   def fromFuture[F[_],A](f: F[A])(using Gopher[F]): ReadChannel[F,A] =
      futureInput(f)

end ReadChannel




