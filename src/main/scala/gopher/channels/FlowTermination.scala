package gopher.channels

import scala.concurrent._
import scala.annotation._

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

