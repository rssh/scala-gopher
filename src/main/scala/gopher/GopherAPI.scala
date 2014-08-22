package gopher

import akka.actor._
import gopher.channels._
import scala.concurrent._
import java.util.concurrent.atomic.AtomicLong

class GopherAPI(as: ActorSystem, es: ExecutionContext)
{

  def select: SelectFactory =
    new SelectFactory(this)

  def makeChannel[A](capacity: Int = 1) =
    {
     val nextId = newChannelId
     channelSupervisorRef ! NewChannel(nextId, capacity)
     val newChannelPath = channelSupervisorRef.path / nextId.toString
     val selection = actorSystem.actorSelection(newChannelPath)
     new IOChannel[A](selection)
    }

  def actorSystem: ActorSystem = as

  def executionContext: ExecutionContext = es

  private[gopher] val continuatedProcessorRef: ActorRef = {
    val props = Props(classOf[ChannelProcessor], this)
    actorSystem.actorOf(props,name="channelProcessor")
  }

  private[gopher] val channelSupervisorRef: ActorRef = {
    val props = Props(classOf[ChannelSupervisor], this)
    actorSystem.actorOf(props,name="channels")
  }

  private[gopher] def newChannelId: Long =
                        channelIdCounter.getAndIncrement

  private[this] val channelIdCounter = new AtomicLong(0L)
  
}
