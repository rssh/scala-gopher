package gopher.channels

import scala.concurrent._

trait FlowTermination[-A]
{

  def doThrow(e: Throwable): Unit

  def doExit(a:A): Unit

  def defer(body: =>Unit)(implicit ec: ExecutionContext):Unit

  def isCompleted: Boolean

}
