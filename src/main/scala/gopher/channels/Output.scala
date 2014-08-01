package gopher.channels

import scala.concurrent._

/**
 * Entity, which can 'eat' objects of type A,
 * can be part of channel
 */
trait Output[A]
{

  def  awrite[B](a: A, cont: Continuated[B] ): Future[Continuated[B]]

}
