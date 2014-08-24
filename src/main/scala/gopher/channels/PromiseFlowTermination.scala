package gopher.channels

import scala.concurrent._

trait PromiseFlowTermination[A] extends FlowTermination[A]
{

  def doThrow(e: Throwable): Unit =
    p failure e

  def doExit(a: A): Unit =
    p success a

  def defer(body: =>Unit)(implicit ec: ExecutionContext): Unit =
    p.future.onComplete{ x => body }

  def future =
    p future

  def isCompleted = p.isCompleted

  val p = Promise[A]()

}

object PromiseFlowTermination
{
  def apply[A]() = new PromiseFlowTermination[A]() {}
}
