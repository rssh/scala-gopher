package gopher.channels


import akka.actor._
import akka.pattern._

import scala.concurrent._
import scala.concurrent.duration._
import scala.util._
import scala.language.experimental.macros
import scala.language.postfixOps
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import gopher._
import gopher.channels.ContRead.In

class ActorBackedChannel[A](futureChannelRef: Future[ActorRef], override val api: GopherAPI) extends Channel[A]
{

  thisActorBackedChannel =>

  def  cbread[B](f: ContRead[A,B] => Option[ContRead.In[A] => Future[Continuated[B]]], flwt: FlowTermination[B] ): Unit = 
  {
   val cont = ContRead(f,this, flwt)
   def applyClosed() =
   {
      f(cont) foreach {  f1 => try {
                                 api.continue( f1(ContRead.ChannelClosed), flwt) 
                               } catch {
                                 case ex: Throwable => flwt.doThrow(ex, cont)
                               }
                      }
   }
   implicit val ec = api.executionContext
   if (closed) {
     if (closedEmpty) {
       applyClosed();
     } else {
         // TODO: ask timeput on closed channel set in config.
         futureChannelRef.foreach{ ref => val f = ref.ask(ClosedChannelRead(cont))(5 seconds)
                                     f.onComplete{
                                         case Failure(e) =>
                                               if (e.isInstanceOf[AskTimeoutException]) {
                                                 applyClosed()
                                               }
                                         case Success(ChannelCloseProcessed(0)) =>
                                               closedEmpty = true
                                         case _ =>  // do nothing
                                     }
                                 }
     }
   } else {
     futureChannelRef.foreach( _ ! cont )
   }
  }

  private def  contRead[B](x:ContRead[A,B]): Unit =
     futureChannelRef.foreach( _ ! x )(api.executionContext)

  def  cbwrite[B](f: ContWrite[A,B] => Option[(A,Future[Continuated[B]])], flwt: FlowTermination[B] ): Unit = {
    val cont = ContWrite(f, this, flwt)
    if (closed) {
      flwt.doThrow(new ChannelClosedException(), cont)
    } else {
      futureChannelRef.foreach(_ ! cont)(api.executionContext)
    }
  }

  private def contWrite[B](x:ContWrite[A,B]): Unit =
    futureChannelRef.foreach( _ ! x )(api.executionContext)

  //private[this] implicit val ec = api.executionContext

  def isClosed: Boolean = closed

  def close(): Unit =
  {
    futureChannelRef.foreach( _ ! ChannelClose )(api.executionContext)
    closed=true
  }

  val done = new Input[Unit] {

    /**
      * apply f, when input will be ready and send result to API processor
      */
    override def cbread[B](f: (ContRead[Unit, B]) => Option[(In[Unit]) => Future[Continuated[B]]], ft: FlowTermination[B]): Unit =
      {
        val cr = ContRead(f,this,ft)
        if (isClosed) {
          applyDone(cr)
        } else {
          futureChannelRef.foreach( _ ! ChannelCloseCallback(cr) )(api.executionContext)
        }
      }

    /**
      * instance of gopher API
      */
    override def api: GopherAPI = thisActorBackedChannel.api
  }


  override protected def finalize(): Unit =
  {
   // allow channel actor be grabage collected
   futureChannelRef.foreach( _ ! ChannelRefDecrement )(api.executionContext)
  }

  private var closed = false
  private var closedEmpty = false
}

