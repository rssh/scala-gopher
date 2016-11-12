package gopher.channels

import akka.actor._
import scala.concurrent._
import gopher._

case class NewChannel(id: Long, capacity: Int)
case class CloseChannel(id: Long)

class ChannelSupervisor(api: GopherAPI) extends Actor
{

   def receive = {
      case NewChannel(id,capacity) => 
             val actorClass = capacity match {
                             case 0 => classOf[UnbufferedChannelActor[_]]
                             case Int.MaxValue => classOf[GrowingBufferedChannelActor[_]]
                             case _ => classOf[BufferedChannelActor[_]]
                           }
             val props = Props(actorClass,id, capacity, api)
             sender ! context.actorOf(props, name=id.toString)
      case CloseChannel(id) =>
             context.actorSelection(id.toString) ! ChannelClose
   }

}
