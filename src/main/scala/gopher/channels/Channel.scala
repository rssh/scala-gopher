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
   
   override def filter(p:A=>Boolean): Channel[A] =
   {
    val filteredInput = super.filter(p)
    new Channel[A] {
         def cbread[B](f:
              ContRead[A,B]=>Option[
                    ContRead.In[A]=>Future[Continuated[B]]
            ],
            ft: FlowTermination[B]): Unit = filteredInput.cbread(f,ft)

         def  cbwrite[B](f: ContWrite[A,B] => Option[
                   (A,Future[Continuated[B]])
                  ],
                  ft: FlowTermination[B]): Unit =
            thisChannel.cbwrite(f,ft)  // TODO: optimize by filteredOutput.cbwrite() ?

        def api = thisChannel.api

        def close() = thisChannel.close()
    }
   }

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
