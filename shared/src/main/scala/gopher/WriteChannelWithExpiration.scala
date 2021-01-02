package gopher

import cps._
import scala.concurrent._
import scala.concurrent.duration._
import scala.util._
import gopher.impl._


/**
 *  Channel, where messages can be exprited.
 **/
class WriteChannelWithExpiration[F[_],A](internal: WriteChannel[F,A], ttl: FiniteDuration, throwTimeouts: Boolean, gopherApi: Gopher[F]) extends WriteChannel[F,A]:


    override def awrite(a:A):F[Unit] =
      val expireTime = System.currentTimeMillis() + ttl.toMillis
      asyncMonad.adoptCallbackStyle(f =>
        internal.addWriter(makeExpirableWriter(a, f, expireTime))
      )

    def addWriter(writer: Writer[A]): Unit =
      val expireTime = System.currentTimeMillis() + ttl.toMillis
      internal.addWriter(wrapExpirable(writer,expireTime))

    def asyncMonad: CpsAsyncMonad[F] =
      internal.asyncMonad

    override def withWriteExpiration(ttl: FiniteDuration, throwTimeouts: Boolean)(using gopherApi: Gopher[F]): WriteChannelWithExpiration[F,A] =
      new WriteChannelWithExpiration(internal, ttl, throwTimeouts, gopherApi)

    private def wrapExpirable(nested: Writer[A], expireTimeMillis: Long) =
      if (throwTimeouts) then
        NestedWriterWithExpireTimeThrowing(nested, expireTimeMillis, gopherApi)
      else
        NesteWriterWithExpireTime(nested, expireTimeMillis)

    private def makeExpirableWriter(a:A, f: Try[Unit]=>Unit, expireTimeMillis: Long): Writer[A] =
      if (throwTimeouts)
        NestedWriterWithExpireTimeThrowing(SimpleWriter(a,f), expireTimeMillis, gopherApi)
      else
        SimpleWriterWithExpireTime(a,f,expireTimeMillis)
    
    
    
