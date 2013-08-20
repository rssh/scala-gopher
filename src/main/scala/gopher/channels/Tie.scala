package gopher.channels




/**
 * Tie is an object which connect one or two channels and process messages between ones.
 * 
 */
trait Tie {
  
  val api: ChannelsAPI
  
  def addReadAction[A](ch: api.IChannel[A], action: ReadAction[A]): Unit
  
  def addWriteAction[A](ch: api.OChannel[A], action: WriteAction[A]): Unit
  
  def setIdleAction(action: IdleAction): Unit
  
  
  /**
   * If implementation require starting of tie before action (for example - when tie contains
   *  thread), than do this action.  In some implementations can do nothing.
   */
  def start();
  
  
  def shutdown();
  
  /**
   * Wait shutdowm.  Can utilize current thread for message processing.
   */
  def waitShutdown();
  
}