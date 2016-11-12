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

trait Channel[A] extends InputOutput[A]
{

   thisChannel =>

   def close(): Unit

   // override some operations

   class Filtered(p:A=>Boolean) extends super.Filtered(p)
                                    with Channel[A]
   {
     def  cbwrite[B](f: ContWrite[A,B] => Option[(A,Future[Continuated[B]])],ft: FlowTermination[B]):Unit =
       thisChannel.cbwrite(f,ft)

     def close() = thisChannel.close()
   }
   
   override def filter(p:A=>Boolean): Channel[A] = new Filtered(p)

}

object Channel
{

  def apply[A](capacity: Int = 0)(implicit api:GopherAPI):Channel[A] = 
  {
     require(capacity >= 0)
     import api._
     val nextId = newChannelId
     val futureChannelRef = (channelSupervisorRef.ask(
                                  NewChannel(nextId, capacity)
                             )(10 seconds)
                              .asInstanceOf[Future[ActorRef]]
                            )

     new ActorBackedChannel[A](futureChannelRef, api)
  }

}
