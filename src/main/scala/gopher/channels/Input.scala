package gopher.channels

import scala.concurrent._

/**
 * Entity, which can 'eat' objects of type A,
 * can be part of channel
 */
trait Input[A]
{

  def  tryRead[B](a: A, f: (A, ContRead[A,B]) => Future[Continuated[B]] ): Future[Continuated[B]]

}
