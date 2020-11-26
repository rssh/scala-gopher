package gopher

import cps._

import scala.util.Try

trait OChannel[F[_]:CpsAsyncMonad, A]:


   def awrite(a:A):F[Unit] =
     summon[CpsAsyncMonad[F]].adoptCallbackStyle(f =>
         addWriter(SimpleWriter(a, f))
     )
   
   inline def write(a:A): Unit = await(awrite(a)) 

   def addWriter(writer: Writer[A]): Unit 
     
   
   class SimpleWriter(a:A, f: Try[Unit]=>Unit) extends Writer[A]:

      def canExpire: Boolean = false

      def isExpired: Boolean = false
  
      def capture(): Option[(A,Try[Unit]=>Unit)] = Some((a,f))

      def markUsed(): Unit = ()

      def markFree(): Unit = ()

