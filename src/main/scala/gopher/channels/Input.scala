package gopher.channels

import scala.concurrent._

/**
 * Entity, which can read (or generate, as you prefer) objects of type A,
 * can be part of channel
 */
trait Input[A]
{

  def  read[B](f: (A, ContRead[A,B]) => Future[Continuated[B]] ): Future[Continuated[B]]

  def  closed: Boolean

}
