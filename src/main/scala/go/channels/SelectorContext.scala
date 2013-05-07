package go.channels

/**
 * context, which await of doing one of blocked operations.
 * We can look on one as on implementation of go select statement:
 * we can add to context pairs of inputs and outputs, and then
 * when some of inputs and outputs are non-blockde, than action is performed.
 * I.e. next block of 'go' select:
 * <pre>
 *  select
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
       
  }
  
  def  addOutputAction[A](channel: OutputChannel[A], action: () => Option[A]): Unit = ???
  
  def  addIddleAction(action: Unit => Unit) = ???

  /**
   * actually run this in loop with waiter in current thread.
   */
  def  runForever(): Unit = ???
  
  /**
   * wait for 1-st event 
   */
  def  runOnce(): Unit = 
  {
    // TODO: initializr cyclicBarrier and run
  }
  
  
  
  /**
   * enable listeners and outputChannels 
   */
  def  go(): Unit = ???
  
 // private val lock = new ReentrantLock()
 // private val condition = new AtomicCondition()
  
  @volatile 
  private var enabled = false;
   
}
