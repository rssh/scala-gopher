package gopher

import akka.actor._
import akka.pattern._
import gopher.channels._
import gopher.transputers._
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.util._
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

  def makeTransputer[T <: Transputer]: T = macro GopherAPI.makeTransputerImpl[T]

  def replicate[T<: Transputer](n:Int): Transputer = macro Replicate.replicateImpl[T]

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

  private[gopher] val transputerSupervisorRef: ActorRef = {
    val props = Props(classOf[TransputerSupervisor], this)
    actorSystem.actorOf(props,name="transputerSupervisor")
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

  //oject RecoveryPolicy {
  //   def AlwaysRestart: PartialFunction[Throwable,SupervisorStrategy.Directive] = 
  //            { case NonFatal(ex) => SupervisorStrategy.Restart }
  //}

  def makeTransputerImpl[T <: Transputer : c.WeakTypeTag](c:Context):c.Expr[T] = {
    import c.universe._
    //----------------------------------------------
    // generate incorrect code: see  https://issues.scala-lang.org/browse/SI-8953
    //c.Expr[T](q"""{ def factory():${c.weakTypeOf[T]} = new ${c.weakTypeOf[T]} { 
    //                                            def api = ${c.prefix} 
    //                                            def recoverFactory = factory
    //                                 }
    //                val retval = factory()
    //                retval
    //              }
    //           """)
    //----------------------------------------------
    // so, let's create subclass
    val implName = c.freshName(c.symbolOf[T].name)
    c.Expr[T](q"""{ 
                    class ${implName} extends ${c.weakTypeOf[T]} { 
                        def api = ${c.prefix}
                        def recoverFactory = () => new ${implName}
                    }
                    new ${implName}
                  }
               """)
  }

}
