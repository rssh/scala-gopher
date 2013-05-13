package go.channels

import java.util.concurrent.{Future => JavaFuture, _ }
import scala.concurrent._

/**
 * context, which await of doing one of blocked operations.
 * We can look on one as on implementation of go select statement:
 * we can add to context pairs of inputs and outputs, and then
 * when some of inputs and outputs are non-blockde, than action is performed.
 * I.e. next block of 'go' select:
 * <pre>
 *  select match
 *    case x <- a => f1(x)
 *    case x <- b => f2(x)
 *    case 1 -> c => f3
 *    case _  => f4
 * </pre>
 *  is mapped do
 * <pre>
 *  slc = new SelectorContext()
 *  slc.addInputAction(a, x=>f1(x))
 *  slc.addInputAction(b, x=>f2(x))
 *  slc.addOutputAction(c, {f4; Some(1) }
 *  slc.addIddleAction(f4)
 *  slc.runOnce()
 * </pre>
 *  (and with help of macroses, can be write as 
 * <pre>
 *  selector match {
 *    case x <- a => f1(x)
 *    case x <- b => f2(x)
 *    case 1 -> c => f3
      case _ => f4
 *  }
 * </pre>
 */
class SelectorContext {
  
  /**
   * called before selector context become running.
   */
  def  addInputAction[A](channel: InputChannel[A], action: A => Boolean): Unit = 
  {
    val l: (A => Boolean) = { a => 
      if (enabled) {
        val retval = try {
                      action(a);
                     } catch {
                       // TODO: handle non-local return differently ?
                       //  (when we have value)
                       case t: Throwable =>
                         lastException = t
                         false
                     }
        if (retval || lastException!=null) { 
          //we know that we have at least yet one await
          latch.countDown()
        }
        retval
      } else 
        false
    }   
    inputListeners = l :: inputListeners 
    channel addListener l
  }
  
  def  addOutputAction[A](channel: OutputChannel[A], action: () => Option[A]): Unit = 
  {
    val l = {() =>
      if (enabled) {
        val retval = try {
                       action()
                     } catch {
                       case t: Throwable =>
                          lastException=t
                          None
                     }
        if (retval.isDefined || lastException!=null) { 
           latch.countDown()
        }
        retval
      } else None
    }
    outputListeners = l :: outputListeners 
    channel addListener l
  }
  
  def  setIddleAction(action: Unit => Unit) = 
  {
    idleAction = action 
  }

  
  /**
   * wait for 1-st event 
   */
  def  runOnce(): Unit = 
  {
    latch = new CountDownLatch(1)
    lastException = null;
    enabled = true
    latch.await(IDLE_MILLISECONDS, TimeUnit.MILLISECONDS)
    enabled = false
    if (lastException != null) {
       throw lastException;
    }
    if (latch.getCount() > 0) {
      latch.countDown()
      idleAction
    }
  }
  
  
  
  /**
   * enable listeners and outputChannels 
   */
  def  go(implicit ex:ExecutionContext): Future[Unit] = 
  {
    Future{ runOnce() } flatMap { (u:Unit) =>
       if (!shutdowned) go
       else Future(u)
    }
  }

  
  def shutdown(): Unit =
  {
    enabled=false
    shutdowned=true
    // allow gc to cleanup listeners.
    inputListeners = Nil
    outputListeners = Nil
  }

  
  private var inputListeners:List[Nothing=>Boolean] = Nil 
  private var outputListeners:List[()=>Option[Any]] = Nil 
  
  @volatile 
  private var enabled = false;
  
  @volatile
  private var shutdowned = true;
  
  @volatile
  private var latch: CountDownLatch = null; 

  @volatile
  private var lastException: Throwable = null;
  
  private val IDLE_MILLISECONDS = 100;
  private var idleAction: Unit => Unit = { (x:Unit) =>  }
  
}
