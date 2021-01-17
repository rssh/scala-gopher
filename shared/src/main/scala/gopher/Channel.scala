package gopher

import cps._
import java.io.Closeable
import scala.concurrent.duration.FiniteDuration

import gopher.impl._

trait Channel[F[_],W,R] extends WriteChannel[F,W] with ReadChannel[F,R] with Closeable:

  override def gopherApi: Gopher[F]

  def withExpiration(ttl: FiniteDuration, throwTimeouts: Boolean): ChannelWithExpiration[F,W,R] =
    new ChannelWithExpiration(this, ttl, throwTimeouts)

  def map[R1](f: R=>R1): Channel[F,W,R1] =
    MappedChannel(this,f)

  def flatMap[R1](f: R=> ReadChannel[F,R1]): Channel[F,W,R1] =
    ChFlatMappedChannel(this,f)


end Channel

object Channel:

  case class Read[F[_],A](a:A,  ch:ReadChannel[F,A]|F[A]) {
    type Element = A
  }
  case class FRead[F[_],A](a:A, ch: F[A])
  case class Write[F[_],A](a: A, ch: WriteChannel[F,A])

  
end Channel

