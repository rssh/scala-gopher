package gopher

import cps._
import gopher.impl._
import scala.util.Try
import scala.util.Success
import scala.util.Failure

trait ReadChannel[F[_], A]:

   type read = A

   // workarround for https://github.com/lampepfl/dotty/issues/10477
   protected def asyncMonad: CpsAsyncMonad[F]

   protected def rAsyncMonad: CpsAsyncMonad[F] = asyncMonad

   def addReader(reader: Reader[A]): Unit

  
   def aread:F[A] = 
      asyncMonad.adoptCallbackStyle(f => addReader(SimpleReader(f)))
                               
   inline def read: A = await(aread)(using rAsyncMonad)

   inline def ? : A = await(aread)(using rAsyncMonad)

   object Read:
     def unapply(): Option[A] = ???

   def aOptRead: F[Option[A]] =
       asyncMonad.adoptCallbackStyle( f =>
                   addReader(SimpleReader{ x => x match
                                            case Failure(ex: ChannelClosedException) => f(Success(None)) 
                                            case Failure(ex) => f(Failure(ex)) 
                                            case Success(v) => f(Success(Some(v))) 
                                         })
       )

   inline def optRead: Option[A] = await(aOptRead)(using rAsyncMonad)

   class SimpleReader(f: Try[A] => Unit) extends Reader[A]:

      def canExpire: Boolean = false
      def isExpired: Boolean = false

      def capture(): Option[Try[A]=>Unit] = Some(f)

      def markUsed(): Unit = ()
      def markFree(): Unit = ()



