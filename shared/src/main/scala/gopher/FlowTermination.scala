package gopher

import scala.annotation._

sealed trait SelectFlow[S]



/**
 * FlowTermination- termination of flow.
 *
 * Inside each block in select loop or
 * select apply (once or forever) we have implicit
 * FlowTermination entity, which we can use for
 * exiting the loop.
 *
 *{{{
 *  select.forever{
 *      case x: info.read => Console.println(s"received from info \$x")
 *      case x: control.read => Select.Done(())
 *  }
 *}}}
 **/
trait FlowTermination:

  /**
   * terminate current flow and leave `a` as result of flow.
   * have no effect if flow is already completed.
   */
  def apply[A](value: A): A 


  
