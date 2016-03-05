package gopher

import akka.actor._
import akka.pattern._
import gopher.channels._
import gopher.transputers._
import scala.concurrent.{Channel=>_,_}
import scala.concurrent.duration._
import scala.language.experimental.macros
import scala.language.postfixOps
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
     new Channel[A](futureChannelRef, this)
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
   * create and start instance of transputer with given recovery policy.
   *@see gopher.Transputer
   */
  def makeTransputer[T <: Transputer](recoveryPolicy:PartialFunction[Throwable,SupervisorStrategy.Directive]): T = macro GopherAPI.makeTransputerImpl2[T]

  def makeTransputer[T <: Transputer]: T = macro GopherAPI.makeTransputerImpl[T]

  /**
   * create transputer which contains <code>n</code> instances of <code>X</code>
   * where ports are connected to the appropriate ports of each instance in paraller.
   * {{{
   *   val persistStep = replicate[PersistTransputer](nDBConnections)
   * }}}
   */
  def replicate[T<: Transputer](n:Int): Transputer = macro Replicate.replicateImpl[T]

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

  def makeTransputerImpl[T <: Transputer : c.WeakTypeTag](c:Context):c.Expr[T] = {
    import c.universe._
    c.Expr[T](q"${c.prefix}.makeTransputer[${weakTypeOf[T]}](gopher.Transputer.RecoveryPolicy.AlwaysRestart)")
  }

  def makeTransputerImpl2[T <: Transputer : c.WeakTypeTag](c:Context)(recoveryPolicy:c.Expr[PartialFunction[Throwable,SupervisorStrategy.Directive]]):c.Expr[T] = {
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
                    val retval = new ${implName}
                    retval.recoverAppend(${recoveryPolicy})
                    retval
                  }
               """)
  }

}
