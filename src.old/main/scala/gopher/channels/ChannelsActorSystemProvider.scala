package gopher.channels

import akka.actor._

trait ChannelsActorSystemProvider
{
  def actorSystem: ActorSystem
}

/**
 * Stab for channels actor-system, which intialized
 */
object DefaultChannelsActorSystemProvider extends ChannelsActorSystemProvider {
  
  override lazy val actorSystem = ActorSystem()

}