package gopher

import cps._
import java.io.Closeable
import scala.concurrent.duration.FiniteDuration

import gopher.impl._

trait Channel[F[_],W,R] extends WriteChannel[F,W] with ReadChannel[F,R] with Closeable:

  override def gopherApi: Gopher[F]

  def withExpiration(ttl: FiniteDuration, throwTimeouts: Boolean): ChannelWithExpiration[F,W,R] =
    new ChannelWithExpiration(this, ttl, throwTimeouts)

  override def map[R1](f: R=>R1): Channel[F,W,R1] =
    MappedChannel(this,f)

  override def mapAsync[R1](f: R=>F[R1]): Channel[F,W,R1] = 
    MappedAsyncChannel(this, f)   

  def flatMap[R1](f: R=> ReadChannel[F,R1]): Channel[F,W,R1] =
    ChFlatMappedChannel(this,f)

  //def flatMapAsync[R1](f: R=> F[ReadChannel[F,R1]]): Channel[F,W,R1] =
  //  ChFlatMappedAsyncChannel(this,f)
  
  override def filter(p: R=>Boolean): Channel[F,W,R] =
    FilteredChannel(this, p)

  override def filterAsync(p: R=>F[Boolean]): Channel[F,W,R] =
    FilteredAsyncChannel(this,p)

  def isClosed: Boolean  


end Channel

object Channel:

  def apply[F[_],A]()(using Gopher[F]): Channel[F,A,A] =
    summon[Gopher[F]].makeChannel[A]()

  case class Read[F[_],A](a:A,  ch:ReadChannel[F,A]|F[A]) {
    type Element = A
  }
  case class FRead[F[_],A](a:A, ch: F[A])
  case class Write[F[_],A](a: A, ch: WriteChannel[F,A])

  
end Channel

