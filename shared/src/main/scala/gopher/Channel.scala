package gopher

import cps._
import java.io.Closeable
import scala.concurrent.duration.FiniteDuration

trait Channel[F[_],W,R] extends WriteChannel[F,W] with ReadChannel[F,R] with Closeable:

  override def gopherApi: Gopher[F]

  def withExpiration(ttl: FiniteDuration, throwTimeouts: Boolean): ChannelWithExpiration[F,W,R] =
    new ChannelWithExpiration(this, ttl, throwTimeouts)


end Channel

