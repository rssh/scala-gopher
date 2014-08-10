package gopher.channels

import akka.actor._
import scala.concurrent._

class IOChannel[A](channelRef: ActorRef) extends Input[A] with Output[A]
{


  def  aread[B](f: (A, ContRead[A,B]) => Option[Future[Continuated[B]]], flwt: FlowTermination[B] ): Unit = 
     channelRef ! ContRead(f,this, flwt)

  private def  contRead[B](x:ContRead[A,B]): Unit =
     channelRef ! x

  def  awrite[B](f: ContWrite[A,B] => Option[(A,Future[Continuated[B]])], flwt: FlowTermination[B] ): Unit = 
    if (closed) {
     throw new IllegalStateException("channel is closed");
    } else {
     channelRef ! ContWrite(f,this, flwt)
    }

  private def contWrite[B](x:ContWrite[A,B]): Unit =
    channelRef ! x

  def isClosed: Boolean = closed

  def close: Unit =
  {
    channelRef ! ChannelClose
    closed=true
  }

  private var closed = false
}
