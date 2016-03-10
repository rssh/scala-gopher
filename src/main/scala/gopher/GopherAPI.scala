package gopher

import akka.actor._
import akka.pattern._
import gopher.channels._
import scala.concurrent.{Channel=>_,_}
import scala.concurrent.duration._
import scala.language.experimental.macros
import scala.language.postfixOps
import scala.reflect.macros.blackbox.Context
import scala.util._
import java.util.concurrent.atomic.AtomicLong
import com.typesafe.config._

class GopherAPI(as: ActorSystem, es: ExecutionContext)
{


  /**
   * obtain channel
   *
   *{{{
   *  val channel = gopherApi.makeChannel[Int]()
   *  channel.awrite(1 to 100)
   *}}}
   */
  def makeChannel[A](capacity: Int = 0) =
    {
     require(capacity >= 0)
     val nextId = newChannelId
     val futureChannelRef = (channelSupervisorRef.ask(
                                  NewChannel(nextId, capacity)
                             )(10 seconds)
                              .asInstanceOf[Future[ActorRef]]
                            )
     
     new ActorBackedChannel[A](futureChannelRef, this)
    }


  /**
   * Represent Scala future as channel from which we can read one value.
   *@see gopher.channels.FutureInput
   */
  def futureInput[A](future:Future[A]): FutureInput[A] = new FutureInput(future, this)

  /**
   * Represent Scala iterable as channel, where all values can be readed in order of iteration.
   */
  def iterableInput[A](iterable:Iterable[A]): Input[A] = Input.asInput(iterable, this)

  /**
   * actor system which was passed during creation
   **/
  def actorSystem: ActorSystem = as

  /**
   * execution context used for managing calculation steps in channels engine.
   **/
  def executionContext: ExecutionContext = es

  /**
   * the configuration of the gopher system. By default is contained under 'gopher' key in top-level config.
   **/
  def config: Config = as.settings.config.atKey("gopher")

  def currentFlow = CurrentFlowTermination

  private[gopher] val idleDetector = new IdleDetector(this)

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

  private[gopher] def continue[A](next:Future[Continuated[A]], ft:FlowTermination[A]): Unit =
                       next.onComplete{
                          case Success(cont) => 
                                              continuatedProcessorRef ! cont
                          case Failure(ex) => ft.throwIfNotCompleted(ex)
                       }(executionContext)
 
  private[this] val channelIdCounter = new AtomicLong(0L)

  
}

object GopherAPI
{


}
