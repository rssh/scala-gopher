package gopher

import akka.actor._
import akka.pattern._
import gopher.channels._
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


  def makeTransputer[T <: Transputer]: T = macro GopherAPI.makeTransputerImpl[T]

  def actorSystem: ActorSystem = as

  def executionContext: ExecutionContext = es

  def config: Config = as.settings.config.atKey("gopher")

  def currentFlow = CurrentFlowTermination

  private[gopher] val transputerSupervisorRef: ActorRef = {
    val props = Props(classOf[TransputerSupervisor], this)
    actorSystem.actorOf(props,name="transputerSupervisor")
  }

  private[gopher] def newChannelId: Long =
                        channelIdCounter.getAndIncrement

  private[this] val channelIdCounter = new AtomicLong(0L)

  
}

object GopherAPI
{

  def makeTransputerImpl[T <: Transputer : c.WeakTypeTag](c:Context):c.Expr[T] = {
    import c.universe._
    c.Expr[T](q"""{ def factory():${c.weakTypeOf[T]} = new ${c.weakTypeOf[T]} { 
                                                def api = ${c.prefix} 
                                                def recoverFactory = factory
                                     }
                    val retval = factory()
                    retval
                  }
               """)
  }

}
