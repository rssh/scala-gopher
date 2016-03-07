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
             val props = Props(classOf[BufferedChannelActor[_]],id, capacity, api)
             sender ! context.actorOf(props, name=id.toString)
      case CloseChannel(id) =>
             context.actorSelection(id.toString) ! ChannelClose
   }

}
