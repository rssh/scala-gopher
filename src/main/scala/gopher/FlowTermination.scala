package gopher

import scala.concurrent._
import scala.annotation._

/**
 * FlowTermination[-A] - termination of flow.
 *
 * Inside each block in select loop or 
 * select apply (once or forever) we have implicit 
 * FlowTermination entity, which we can use for
 * exiting the loop.
 *
 *{{{
 *  select.forever{
 *      case x: info.read => Console.println(s"received from info \$x")
 *      case x: control.read => implicitly[FlowTermination[Unit]].doExit(())
 *  }
 *}}}
 **/
trait FlowTermination[-A]
{

  /**
   * terminate current flow with exception.
   * Mostly used internally.
   */
  def doThrow(e: Throwable): Unit

  /**
   * terminate current flow and leave `a` as result of flow.
   * have no effect if flow is already completed.
   */
  def doExit(a:A): A@unchecked.uncheckedVariance

  /**
   * check - if flow is completed. 
   */
  def isCompleted: Boolean

  def throwIfNotCompleted(ex: Throwable): Unit 


}

