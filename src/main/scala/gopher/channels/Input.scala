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
  def  cbread[B](f: (A, ContRead[A,B]) => Option[Future[Continuated[B]]], flwt: FlowTermination[B] ): Unit

  def  aread:Future[A] = {
    val ft = PromiseFlowTermination[A]() 
    cbread[A]( (a, self) => { Some(Future.successful(Done(a,ft))) }, ft )
    ft.future
  }


}
