package gopher

import cps._
import scala.util.Try
import scala.util.Success
import scala.util.Failure

trait IChannel[F[_], A]:

   type Read = A

   def addReader(reader: Reader[A]): Unit

   // workarround for https://github.com/lampepfl/dotty/issues/10477
   protected def m: CpsAsyncMonad[F]
  
   def aread:F[A] = 
      m.adoptCallbackStyle(f => addReader(SimpleReader(f)))
                               
   inline def read: A = await(aread)(using m)

   inline def ? : A = await(aread)(using m)

   def aOptRead: F[Option[A]] =
       m.adoptCallbackStyle( f =>
                   addReader(SimpleReader{ x => x match
                                            case Failure(ex: ChannelClosedException) => f(Success(None)) 
                                            case Failure(ex) => f(Failure(ex)) 
                                            case Success(v) => f(Success(Some(v))) 
                                         })
       )

   inline def optRead: Option[A] = await(aOptRead)(using m)

   class SimpleReader(f: Try[A] => Unit) extends Reader[A]:

      def canExpire: Boolean = false
      def isExpired: Boolean = false

      def capture(): Option[Try[A]=>Unit] = Some(f)

      def markUsed(): Unit = ()
      def markFree(): Unit = ()



