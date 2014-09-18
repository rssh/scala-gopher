package gopher

import scala.concurrent._
import scala.annotation._

trait FlowTermination[-A]
{

  def doThrow(e: Throwable): Unit

  def doExit(a:A): Unit

  def defer(body: =>Unit)(implicit ec: ExecutionContext):Unit

  def isCompleted: Boolean

}


class CompileTimeOnlyFlowTermination[A] extends FlowTermination[A]
{

  @compileTimeOnly("Usage of implicit FlowTermination outside gopher flow")
  def doThrow(e: Throwable): Unit = ???

  @compileTimeOnly("Usage of implicit FlowTermination outside gopher flow")
  def doExit(a:A): Unit = ???

  @compileTimeOnly("Usage of implicit FlowTermination outside gopher flow")
  def defer(body: =>Unit)(implicit ec: ExecutionContext):Unit = ???

  @compileTimeOnly("Usage of implicit FlowTermination outside gopher flow")
  def isCompleted: Boolean = ???

}
