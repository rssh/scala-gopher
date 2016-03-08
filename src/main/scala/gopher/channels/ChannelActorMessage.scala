package gopher.channels

import scala.language.existentials

sealed trait ChannelActorMessage

case object ChannelClose extends ChannelActorMessage

/**
 * this is message wich send to ChannelActor, when we 
 * know, that channel is closed. In such case, we don't
 * konw: is actor stopped or not, So, we say this message
 * (instead read) and wait for reply. If reply is not received
 * within given timeout: think that channel is-dead.
 */
case class ClosedChannelRead(cont: ContRead[_,_]) extends ChannelActorMessage

/**
 * this message is send, when all references to 
 * some instance of this channel are unreachable, 
 * so if we have no other instances (i.e. remote 
 * channel incarnation), than we must destroy channel.
 **/
case object ChannelRefDecrement extends ChannelActorMessage

/**
 * this message is send, when we create new remote 
 * reference to channel, backed by this actor.
 **/
case object ChannelRefIncrement extends ChannelActorMessage

/**
 * result of CloseChannelRead, return number of elements
 * left to read
 */
case class ChannelCloseProcessed(nElements: Int) extends ChannelActorMessage

/**
 * When we decide to stop channel, do it via special message,
 * to process one after messages, which exists now in queue.
 *
 * Note, that channel-stop messages can be send only from ChannelActor
 */
case object GracefullChannelStop extends ChannelActorMessage

