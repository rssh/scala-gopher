package gopher.channels

import akka.actor._
import scala.concurrent._

case class NewChannel(id: Long, capacity: Int)
case class CloseChannel(id: Long)

class ChannelSupervisor(api: API) extends Actor
{

   def receive = {
      case NewChannel(id,capacity) => 
             val props = Props(classOf[ChannelActor[_]],id, capacity, api)
             context.actorOf(props, name=id.toString)
      case CloseChannel(id) =>
             context.actorSelection(id.toString) ! ChannelClose
   }

}
