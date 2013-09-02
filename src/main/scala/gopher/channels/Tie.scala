package gopher.channels

import scala.concurrent._


/**
 * Tie is an object which connect one or two channels and process messages between ones.
 * 
 */
trait Tie[ API <: ChannelsAPI[API] ] extends TieJoin {
 
  thisTie =>
  
  def addReadAction[A](ch: API#IChannel[A], action: ReadAction[A]): Unit
  
  def addWriteAction[A](ch: API#OChannel[A], action: WriteAction[A]): Unit
  
  def setIdleAction(action: IdleAction): Unit
  
  def readJoin[A](ch:API#IChannel[A]) = new TieReadJoin[A] {
    @inline
    def putNext(action: ReadAction[A]): Unit = addReadAction(ch,action)
    @inline
    def processExclusive[A](f: =>A, whenLocked: =>A):A =
      thisTie.processExclusive(f, whenLocked)
    @inline
    def shutdown() = thisTie.shutdown() 
  }
  
  def writeJoin[A](ch:API#OChannel[A]) = new TieWriteJoin[A] {
    @inline
    def putNext(action: WriteAction[A]): Unit = addWriteAction(ch,action)
    @inline
    def processExclusive[A](f: =>A, whenLocked: =>A):A =
      thisTie.processExclusive(f, whenLocked)
    @inline
    def shutdown() = thisTie.shutdown()     
  }
  
  /**
   * If implementation require starting of tie before action (for example - when tie contains
   *  thread), than do this action.  In some implementations can do nothing.
   */
  def start()
  
  /**
   * shutdown tea.
   */
  def shutdown();
  
  /**
   * Wait shutdowm.  Can utilize current thread for message processing or erased by enclosed go macros.
   */
  def waitShutdown();
 
  /**
   *  return Future which is fired when tie is shutdowned.
   */ 
  def shutdownFuture: Future[Unit]
  
  /**
   * If tie is exclusive, try or run f, if nothing is running
   * in the same exclusive mode or perform <codde> whenLocked </code>
   */
  def processExclusive[A](f: => A, whenLocked: =>A):A
  
}