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

   def close(): Unit

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
