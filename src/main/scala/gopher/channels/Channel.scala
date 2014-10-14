package gopher.channels


import akka.actor._
import akka.pattern._
import scala.concurrent._
import scala.concurrent.duration._
import gopher._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._

// TODO: change channelRef to channelSelector
class IOChannel[A](futureChannelRef: Future[ActorRef], override val api: GopherAPI) extends Input[A] with Output[A]
{

  def  cbread[B](f: ContRead[A,B] => Option[ContRead.In[A] => Future[Continuated[B]]], flwt: FlowTermination[B] ): Unit = 
  {
   val cont = ContRead(f,this, flwt)
   def applyClosed() =
   {
      f(cont) foreach { f1 => (api.continuatedProcessorRef ! f1(ContRead.ChannelClosed)) }
   }
   if (closed) {
     if (closedEmpty) {
       applyClosed();
     } else {
         futureChannelRef.foreach{ ref => val f = ref.ask(ClosedChannelRead(cont))(10 seconds)
                                     f.onFailure{
                                          case e: AskTimeoutException => applyClosed()
                                     }
                                     f.onSuccess{
                                          case ChannelCloseProcessed(0) =>
                                                                  closedEmpty = true
                                     }
                                 }
     }
   } else {
     futureChannelRef.foreach( _ ! cont )
   }
  }

  private def  contRead[B](x:ContRead[A,B]): Unit =
     futureChannelRef.foreach( _ ! x )

  def  cbwrite[B](f: ContWrite[A,B] => Option[(A,Future[Continuated[B]])], flwt: FlowTermination[B] ): Unit = 
    if (closed) {
      flwt.doThrow(new ChannelClosedException())
    } else {
     futureChannelRef.foreach( _ ! ContWrite(f,this, flwt) )
    }

  private def contWrite[B](x:ContWrite[A,B]): Unit =
    futureChannelRef.foreach( _ ! x )

  private[this] implicit val ec = api.executionContext

  def isClosed: Boolean = closed

  def close(): Unit =
  {
    futureChannelRef.foreach( _ ! ChannelClose )
    closed=true
  }

  override protected def finalize(): Unit =
  {
   // allow channel actor be grabage collected
   futureChannelRef.foreach( _ ! ChannelRefDecrement )
  }

  private var closed = false
  private var closedEmpty = false
}

