package gopher

import akka.actor._
import akka.pattern._
import gopher.channels._
import scala.concurrent._
import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicLong
import com.typesafe.config._

/**
 * Api for providing access to channel and selector interfaces.
 */
class GopherAPI(as: ActorSystem, es: ExecutionContext)
{

  /**
   * obtain select factory
   *
   * {{{
   *  goopherApi.select.once[String] {
   *    case x: a.read => s"\${x} from A"
   *    case x: b.read => s"\${x} from B"
   *    case _ => "IDLE"
   *  }
   * }}}
   */
  def select: SelectFactory =
    new SelectFactory(this)

  /**
   * obtain channel
   *
   *{{{
   *  val channel = gopherApi.makeChannel[Int]()
   *  channel.awrite(1 to 100)
   *}}}
   */
  def makeChannel[A](capacity: Int = 1) =
    {
     val nextId = newChannelId
     val futureChannelRef = (channelSupervisorRef.ask(
                                  NewChannel(nextId, capacity)
                             )(10 seconds)
                              .asInstanceOf[Future[ActorRef]]
                            )
     new IOChannel[A](futureChannelRef, this)
    }

  def futureInput[A](future:Future[A]): FutureInput[A] = new FutureInput(future, this)

  def iterableInput[A](iterable:Iterable[A]): Input[A] = Input.asInput(iterable, this)

  def actorSystem: ActorSystem = as

  def executionContext: ExecutionContext = es

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

  private[this] val channelIdCounter = new AtomicLong(0L)
  
}
