package gopher.channels

import scala.concurrent._

/**
 * Entity, which can read (or generate, as you prefer) objects of type A,
 * can be part of channel
 */
trait Input[A]
{

  /**
   * apply f, when input will be ready and send result to API processor
   */
  def  aread[B](f: (A, ContRead[A,B]) => Option[Future[Continuated[B]]] ): Unit

  def  read:Future[A] = {
    val p = Promise[A]()
    aread[Unit]{(a, self) => p.success(a); Some(Future.successful(Done(()))) }
    p.future
  }


}
