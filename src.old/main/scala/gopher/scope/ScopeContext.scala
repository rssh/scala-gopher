package gopher.scope

import scala.collection.mutable.Stack
import scala.collection.mutable.Queue
import scala.annotation.tailrec

/**
 * scope context for scope, wich wrap function returning  R
 * and provide interface, for implementing go-like scope.
 * 
 * Direct usage is something like
 * <pre>
 * val scope = new gopher.scope.ScopeContext[X]();
 * scope.eval {
 *   val x = openFile()
 *   scope.defer{ x.close() } 
 *   ....
 * }  
 * </pre>
 *
 * It can be sugared by macro interfaces
 * <pre>
 *  goScope {
 *    val x = openFile()
 *    defer{ x.close() }
 *    ....
 *  }  
 * </pre>
 * 
 */
class ScopeContext
{

  def pushDefer(x: => Unit) =
    if (!done) {
      defers.push( (u:Unit) => x )
    } else {
      throw new IllegalStateException("Attempt to use scope context in done state");
    }
 

  def unwindDefer[R]: Option[R] =
  {
    val x = defers.pop()
    try {
      x()
      None
    } catch {
      case e: RecoverThrowable[_] if e.sc eq this => Some((e.asInstanceOf[RecoverThrowable[R]]).retval)
      case e: Exception => {
                   // TODO: think about policy to handle exceptions from defer ?
                   suppressed += e  
                   None
              }
    }
  }

  def unwindDefers[R]: Option[R] =
  {
    var r: Option[R] = None
    while(defers.nonEmpty && r.isEmpty) {
       r = unwindDefer
    }
    r
  }

  final def eval[R](x: =>R):R =
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
           unwindDefers[R] match {
              case x@Some(r) => 
                         if (optR == None) {
                             optR = x
                             inPanic = false
                         } else {
                             // impossible, mark as suppresed exception
                             val e = new IllegalStateException("second recover in defer sequence")
                             e.fillInStackTrace();
                             suppressed += e
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
  
  def panic(x:String):Nothing = throw new PanicException(x,this)

  
  
  def recover[R](x: R): Unit =
    if (inPanic) {
      throw new RecoverThrowable(x,this);
    } 
   
  /**
   * throw fist suppressed exception if any
   */
  def throwSuppressed: Unit =
    for(e <- suppressed.headOption) {
       throw e
    }
  
  def suppressedExceptions = suppressed.toList

  private val defers: Stack[(Unit=>Unit)] = new Stack()
  private val suppressed: Queue[Exception] = new Queue()
  @volatile private[this] var inPanic:  Boolean = false
  @volatile private[this] var done:  Boolean = false


}
