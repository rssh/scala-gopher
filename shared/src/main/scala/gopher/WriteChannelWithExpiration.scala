package gopher

import cps._
import scala.concurrent._
import scala.concurrent.duration._
import scala.util._
import gopher.impl._


/**
 *  Channel, where messages can be exprited.
 **/
class WriteChannelWithExpiration[F[_],A](internal: WriteChannel[F,A], ttl: FiniteDuration) extends WriteChannel[F,A]:

    override def awrite(a:A):F[Unit] =
      val expireTime = System.currentTimeMillis() + ttl.toMillis
      asyncMonad.adoptCallbackStyle(f =>
        internal.addWriter(SimpleWriterWithExpireTime(a, f, expireTime))
      )

    def addWriter(writer: Writer[A]): Unit =
      val expireTime = System.currentTimeMillis() + ttl.toMillis
      internal.addWriter(NesteWriterWithExpireTime(writer,expireTime))

    def asyncMonad: CpsAsyncMonad[F] =
      internal.asyncMonad

    override def withExpiration(ttl: FiniteDuration): WriteChannelWithExpiration[F,A] =
      new WriteChannelWithExpiration(internal, ttl)
    
    
    
