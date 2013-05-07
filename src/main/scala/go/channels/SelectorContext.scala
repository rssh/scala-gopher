package go.channels

import java.util.concurrent._

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
  
  def  addInputAction[A](channel: InputChannel[A], action: A => Boolean): Unit = 
  {
    // TODO: keep listener in list, for saving from gc
    channel.addListener{ a => 
      if (enabled) {
        val retval = action(a);
        //we know that we have at least yet one await
        latch.countDown()
        retval
      } else 
        false
    }   
  }
  
  def  addOutputAction[A](channel: OutputChannel[A], action: () => Option[A]): Unit = 
  {
    // TODO: keep listener in list, for saving from gc
    channel.addListener{() =>
      if (enabled) {
        val retval = action()
        latch.countDown()
        retval
      } else None
    }
  }
  
  def  setIddleAction(action: Unit => Unit) = 
  {
    idleAction
  }

  /**
   * actually run this in loop with waiter in current thread.
   */
  def  runForever(): Unit = 
  {
    while(!shutdown) {
      runOnce()
    }
  }
  
  /**
   * wait for 1-st event 
   */
  def  runOnce(): Unit = 
  {
    // TODO: handle exception
    // TODO: 
    latch = new CountDownLatch(1)
    enabled = true
    latch.await(IDLE_MILLISECONDS, TimeUnit.MILLISECONDS)
    enabled = false
    if (latch.getCount() > 0) {
      latch.countDown()
      idleAction
    }
  }
  
  
  
  /**
   * enable listeners and outputChannels 
   */
  def  go(): Unit = ???
  
  def shutdown(): Unit =
  {
    enabled=false
    shutdown=true
    // TODO: clear gc-ssaving lists.
  }
  
  
  
  @volatile 
  private var enabled = false;
  
  @volatile
  private var shutdown = true;
  
  @volatile
  private var latch: CountDownLatch = null; // new CountDownLatch(1)
  
  private val IDLE_MILLISECONDS = 100;
  private var idleAction: Unit => Unit = { (x:Unit) =>  }
  
}
