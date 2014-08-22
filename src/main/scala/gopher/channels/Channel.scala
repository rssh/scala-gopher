package gopher.channels

import akka.actor._
import scala.concurrent._

class IOChannel[A](channelSelection: ActorSelection) extends Input[A] with Output[A]
{


  def  cbread[B](f: (A, ContRead[A,B]) => Option[Future[Continuated[B]]], flwt: FlowTermination[B] ): Unit = 
     channelSelection ! ContRead(f,this, flwt)

  private def  contRead[B](x:ContRead[A,B]): Unit =
     channelSelection ! x

  def  cbwrite[B](f: ContWrite[A,B] => Option[(A,Future[Continuated[B]])], flwt: FlowTermination[B] ): Unit = 
    if (closed) {
     throw new IllegalStateException("channel is closed");
    } else {
     channelSelection ! ContWrite(f,this, flwt)
    }

  private def contWrite[B](x:ContWrite[A,B]): Unit =
    channelSelection ! x

  def isClosed: Boolean = closed

  def close: Unit =
  {
    channelSelection ! ChannelClose
    closed=true
  }

  private var closed = false
}
