package gopher.channels


import akka.actor._
import scala.concurrent._
import gopher._

class IOChannel[A](futureChannelRef: Future[ActorRef], api: GopherAPI) extends Input[A] with Output[A]
{


  def  cbread[B](f: (A, ContRead[A,B]) => Option[Future[Continuated[B]]], flwt: FlowTermination[B] ): Unit = 
     futureChannelRef.foreach( _ ! ContRead(f,this, flwt) )

  private def  contRead[B](x:ContRead[A,B]): Unit =
     futureChannelRef.foreach( _ ! x )

  def  cbwrite[B](f: ContWrite[A,B] => Option[(A,Future[Continuated[B]])], flwt: FlowTermination[B] ): Unit = 
    if (closed) {
     throw new IllegalStateException("channel is closed");
    } else {
     futureChannelRef.foreach( _ ! ContWrite(f,this, flwt) )
    }

  private def contWrite[B](x:ContWrite[A,B]): Unit =
    futureChannelRef.foreach( _ ! x )

  private[this] implicit val ec = api.executionContext

  def isClosed: Boolean = closed

  def close: Unit =
  {
    futureChannelRef.foreach( _ ! ChannelClose )
    closed=true
  }

  private var closed = false
}
