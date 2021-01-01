package gopher

import cps._
import gopher.impl._
import scala.concurrent.duration.FiniteDuration

class ChannelWithExpiration[F[_],W,R](internal: Channel[F,W,R], ttl: FiniteDuration) 
                                                        extends WriteChannelWithExpiration[F,W](internal, ttl)
                                                           with Channel[F,W,R]:


  override def gopherApi: Gopher[F] = internal.gopherApi

  override def asyncMonad: CpsSchedulingMonad[F] = gopherApi.asyncMonad

  override def addReader(reader: Reader[R]): Unit =
    internal.addReader(reader)

  override def addDoneReader(reader: Reader[Unit]): Unit =
    internal.addDoneReader(reader)
  
    
  override def withExpiration(ttl: FiniteDuration): ChannelWithExpiration[F,W,R] =
      new ChannelWithExpiration(internal , ttl)
    

  override def close(): Unit = internal.close()


  def qqq: Int = 0

   