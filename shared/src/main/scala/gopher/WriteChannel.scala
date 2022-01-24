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

   transparent inline def write(inline a:A)(using CpsMonadContext[F]): Unit = await(awrite(a))(using asyncMonad)

   @targetName("write1")
   transparent inline def <~ (inline a:A)(using CpsMonadContext[F]): Unit = await(awrite(a))(using asyncMonad) 

   @targetName("write2")
   transparent inline def ! (inline a:A)(using CpsMonadContext[F]): Unit = await(awrite(a))(using asyncMonad) 


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
            val v = it.next()
            write(v)
        }
      }

   transparent inline def writeAll(inline collection: IterableOnce[A])(using mc: CpsMonadContext[F]): Unit = 
      await(awriteAll(collection))(using asyncMonad, mc)


   def withWriteExpiration(ttl: FiniteDuration, throwTimeouts: Boolean)(using gopherApi: Gopher[F]): WriteChannelWithExpiration[F,A] =
       new WriteChannelWithExpiration(this, ttl, throwTimeouts, gopherApi)


   


