package gopher

import gopher.channels._
import gopher.transputers._
import scala.concurrent.{Channel=>_,_}
import scala.concurrent.duration._
import scala.language.experimental.macros
import scala.language.postfixOps
import scala.reflect.macros.blackbox.Context
import scala.util._
import java.util.concurrent.atomic.AtomicLong

/**
 * Api for providing access to channel and selector interfaces.
 */
class GopherAPI
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

  def make[T](args: Any*): T = macro GopherAPI.makeImpl[T]

  /**
   * obtain channel
   *
   *{{{
   *  val channel = gopherApi.makeChannel[Int]()
   *  channel.awrite(1 to 100)
   *}}}
   */
  @inline
  def makeChannel[A](capacity: Int = 0): Channel[A] = ???

  def makeEffectedInput[A](in: Input[A], threadingPolicy: ThreadingPolicy = ThreadingPolicy.Single): EffectedInput[A] =
     EffectedInput(in,threadingPolicy)

  def makeEffectedOutput[A](out: Output[A], threadingPolicy: ThreadingPolicy = ThreadingPolicy.Single) =
     EffectedOutput(out,threadingPolicy)

  def makeEffectedChannel[A](ch: Channel[A], threadingPolicy: ThreadingPolicy = ThreadingPolicy.Single) =
     EffectedChannel(ch,threadingPolicy)

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
   * execution context used for managing calculation steps in channels engine.
   **/
  def executionContext: ExecutionContext = ???

  def currentFlow = CurrentFlowTermination


  private[gopher] def newChannelId: Long =
                        channelIdCounter.getAndIncrement

  private[gopher] def continue[A](next:Future[Continuated[A]], ft:FlowTermination[A]): Unit = ???
 
  private[this] val channelIdCounter = new AtomicLong(0L)

  
}

object GopherAPI
{

  def makeImpl[T : c.WeakTypeTag](c:Context)(args: c.Expr[Any]*): c.Expr[T] = {
    import c.universe._
    val wt = weakTypeOf[T]
    if (wt.companion =:= NoType) {
      c.abort(c.prefix.tree.pos,s"type ${wt.typeSymbol} have no companion")
    } 
    val sym = wt.typeSymbol.companion
    val r = q"${sym}.apply[..${wt.typeArgs}](..${args})(${c.prefix})"
    c.Expr[T](r)
  }



}
