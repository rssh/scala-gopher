package gopher.channels


import akka.actor._
import akka.pattern._
import gopher._

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.experimental.macros
import scala.language.postfixOps

trait Channel[A] extends CloseableInputOutput[A,A]
{

   thisChannel =>

   def close(): Unit

   // override some operations

   class FilteredChannel(p:A=>Boolean) extends FilteredIOC(p)
                                    with Channel[A]
    {
        override def close() = thisChannel.close()
    }

   override def filter(p:A=>Boolean): Channel[A] = new FilteredChannel(p)


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
