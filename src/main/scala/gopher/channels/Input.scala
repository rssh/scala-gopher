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
  def  aread[B](f: (A, ContRead[A,B]) => Option[Future[Continuated[B]]], flwt: FlowTermination[B] ): Unit

  def  read:Future[A] = {
    val p = Promise[A]()
    val ft = new FlowTermination[Unit] {
                def doThrow(ex:Throwable) = p.failure(ex)
                def doExit(u:Unit): Unit = { }
             }
    aread[Unit]( (a, self) => { p.success(a); Some(Future.successful(Done((),ft))) }, ft )
    p.future
  }


}
