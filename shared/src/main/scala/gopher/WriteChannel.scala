package gopher

import cps._
import gopher.impl._

import scala.util.Try

trait WriteChannel[F[_], A]:

   protected def asyncMonad: CpsAsyncMonad[F]

   def awrite(a:A):F[Unit] =
      asyncMonad.adoptCallbackStyle(f =>
         addWriter(SimpleWriter(a, f))
      )
   
   inline def write(a:A): Unit = await(awrite(a))(using asyncMonad) 

   def addWriter(writer: Writer[A]): Unit 
     
   def awriteAll(collection: IterableOnce[A]): F[Unit] = 
      inline given CpsAsyncMonad[F] = asyncMonad
      async[F]{
         val it = collection.iterator
         while(it.hasNext) {
            write(it.next())
        }
      }

   inline def writeAll(collection: IterableOnce[A]): Unit = 
      await(awriteAll(collection))(using asyncMonad)

   class SimpleWriter(a:A, f: Try[Unit]=>Unit) extends Writer[A]:

      def canExpire: Boolean = false

      def isExpired: Boolean = false
  
      def capture(): Option[(A,Try[Unit]=>Unit)] = Some((a,f))

      def markUsed(): Unit = ()

      def markFree(): Unit = ()

