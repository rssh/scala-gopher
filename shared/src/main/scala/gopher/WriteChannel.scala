package gopher

import cps._
import gopher.impl._

import scala.annotation.targetName
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

trait WriteChannel[F[_], A]:

   type write = A

   def asyncMonad: CpsAsyncMonad[F]

   def awrite(a:A):F[Unit] =
      asyncMonad.adoptCallbackStyle(f =>
         addWriter(SimpleWriter(a, f))
      )
   
   //object write:   
   //   inline def apply(a:A): Unit = await(awrite(a))(using asyncMonad) 
   //   inline def unapply(a:A): Some[A] = ???

   inline def write(a:A): Unit = await(awrite(a))(using asyncMonad)

   @targetName("write1")
   inline def <~ (a:A): Unit = await(awrite(a))(using asyncMonad) 

   @targetName("write2")
   inline def ! (a:A): Unit = await(awrite(a))(using asyncMonad) 


   //def Write(x:A):WritePattern = new WritePattern(x)

   //class WritePattern(x:A):
   //    inline def unapply(y:Any): Option[A] = 
   //      Some(x)

   //TODO: make protected[gopher]
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


   def withWriteExpiration(ttl: FiniteDuration, throwTimeouts: Boolean)(using gopherApi: Gopher[F]): WriteChannelWithExpiration[F,A] =
       new WriteChannelWithExpiration(this, ttl, throwTimeouts, gopherApi)


   


