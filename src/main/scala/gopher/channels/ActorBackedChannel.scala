package gopher.channels


import akka.actor._
import akka.pattern._
import scala.concurrent._
import scala.concurrent.duration._
import gopher._
import scala.language.experimental.macros
import scala.language.postfixOps
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._

class ActorBackedChannel[A](futureChannelRef: Future[ActorRef], override val api: GopherAPI) extends Channel[A]
{

  def  cbread[B](f: ContRead[A,B] => Option[ContRead.In[A] => Future[Continuated[B]]], flwt: FlowTermination[B] ): Unit = 
  {
   val cont = ContRead(f,this, flwt)
   def applyClosed() =
   {
      f(cont) foreach {  f1 => try {
                                 api.continue( f1(ContRead.ChannelClosed), flwt) 
                               } catch {
                                 case ex: Throwable => flwt.doThrow(ex)
                               }
                      }
   }
   if (closed) {
     if (closedEmpty) {
       applyClosed();
     } else {
         // TODO: ask timeput on closed channel set in config.
         futureChannelRef.foreach{ ref => val f = ref.ask(ClosedChannelRead(cont))(5 seconds)
                                     f.onFailure{
                                          case e: AskTimeoutException => applyClosed()
                                     }
                                     f.onSuccess{
                                          case ChannelCloseProcessed(0) =>
                                                                  closedEmpty = true
                                     }
                                 }(api.executionContext)
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

