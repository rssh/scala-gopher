package gopher.channels

import akka.actor._
import scala.concurrent._

trait API
{

  def makeChannel(capacity: Int)

  def actorSystem: ActorSystem

  def executionContext: ExecutionContext

  def continuatedProcessorRef: ActorRef

  private[channels] def newChannelId: Long
  
}
