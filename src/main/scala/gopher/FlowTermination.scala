package gopher

import scala.concurrent._
import scala.annotation._

trait FlowTermination[-A]
{

  def doThrow(e: Throwable): Unit

  def doExit(a:A): Unit

  def isCompleted: Boolean

  def throwIfNotCompleted(ex: Throwable): Unit 

}

