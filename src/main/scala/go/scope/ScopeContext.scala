package go.scope

import scala.collection.mutable.Stack
import scala.collection.mutable.Queue
import scala.annotation.tailrec

/**
 * scope context of go function, which returns A
 */
class ScopeContext[R]
{

  def pushDefer(x: => Unit) =
    if (!done) {
      defers.push( (u:Unit) => x )
    } else {
      throw new IllegalStateException("Attempt to use scope context in done state");
    }
 

  def unwindDefer: Option[R] =
  {
    val x = defers.pop()
    try {
      x()
      None
    } catch {
      case e: RecoverThrowable[_] if e.sc eq this => Some((e.asInstanceOf[RecoverThrowable[R]]).retval)
      case e: Exception => {
                   // TODO: think about policy to handle exceptions from defer ?
                   suppressedExceptions += e  
                   None
              }
    }
  }

  def unwindDefers: Option[R] =
  {
    var r: Option[R] = None
    while(defers.nonEmpty && r.isEmpty) {
       r = unwindDefer
    }
    r
  }

  private[scope] final def eval(x: =>R):R =
  {
    var panicEx: Throwable = null;
    var optR: Option[R] = None
    done = false
    try {
        optR = Some(x)
    } catch {
      case ex: Exception => panicEx = ex;
                            inPanic = true
    } finally {
      while(defers.nonEmpty) {
           unwindDefers match {
              case x@Some(r) => 
                         if (optR == None) {
                             optR = x
                             inPanic = false
                         } else {
                             // impossible, mark as suppresed exception
                             val e = new IllegalStateException("second recover in defer sequence")
                             e.fillInStackTrace();
                             suppressedExceptions += e
                         }
              case None => /* do nothing */
           }
      }
    } 
    // TODO: think what to do with suppressedExceptions.
    // may be return 'go-blcok results' ?
    done = true
    optR getOrElse (throw panicEx)
  }

  def recover(x: R): Unit =
    if (inPanic) {
      throw new RecoverThrowable(x,this);
    } 
   
  /**
   * throw fist suppressed exception if any
   */
  def throwSuppressed: Unit =
    for(e <- suppressedExceptions.headOption) {
       throw e
    }

  val defers: Stack[(Unit=>Unit)] = new Stack()
  val suppressedExceptions: Queue[Exception] = new Queue()
  @volatile private[this] var inPanic:  Boolean = false
  @volatile private[this] var done:  Boolean = false


}
