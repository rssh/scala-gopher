package gopher.channels

import scala.concurrent._
import scala.util._
import akka.actor._
import ch.qos.logback.classic.{Logger=>LogbackLogger}



/**
 * Tie is an object which connect one or two channels and process messages between ones.
 * 
 */
trait Tie[ API <: ChannelsAPI[API] ] extends TieJoin {
 
  thisTie =>
  
  def addReadAction[A](ch: API#IChannel[A], action: ReadAction[A]): this.type
  
  def addWriteAction[A](ch: API#OChannel[A], action: WriteAction[A]): this.type
  
  def setIdleAction(action: IdleAction): this.type
  
  def readJoin[A](ch:API#IChannel[A]) = new TieReadJoin[A] {
    @inline
    def putNext(action: ReadAction[A]): Unit = addReadAction(ch,action)
    @inline
    def processExclusive[A](f: => Future[A], whenLocked: =>A): Future[A] =
      thisTie.processExclusive(f, whenLocked)
    @inline
    def shutdown() = thisTie.shutdown()

    @inline
    implicit def actorSystem: akka.actor.ActorSystem = thisTie.actorSystem
    
    @inline
    implicit def executionContext: scala.concurrent.ExecutionContext = thisTie.executionContext

    
  }
  
  def writeJoin[A](ch:API#OChannel[A]) = new TieWriteJoin[A] {
    @inline
    def putNext(action: WriteAction[A]): Unit = addWriteAction(ch,action)
    @inline
    def processExclusive[A](f: => Future[A], whenLocked: =>A): Future[A] =
      thisTie.processExclusive(f, whenLocked)
    @inline
    def shutdown() = thisTie.shutdown()     
    @inline
    implicit def actorSystem: akka.actor.ActorSystem = thisTie.actorSystem
    @inline
    implicit def executionContext: scala.concurrent.ExecutionContext = thisTie.executionContext
  }
  
  /**
   * If implementation require starting of tie before action (for example - when tie contains
   *  thread), than do this action.  In some implementations can do nothing.
   */
  def start(): this.type
  /**
   * shutdown tea  (and activate next tie if set)
   */
  def shutdown(): Unit
   
  /**
   *  return Future which is fired when tie is shutdowned.
   */ 
  def shutdownFuture: Future[Unit]
  
  /**
   * If tie is exclusive, try or run f, if nothing is running
   * in the same exclusive mode or perform <code> whenLocked </code>
   */
  def processExclusive[A](f: => Future[A], whenLocked: =>A): Future[A]
  
  /**
   * 
   */
  def next(implicit api: ChannelsAPI[API]): Tie[API] =
  {
    implicit val ec = executionContext
    implicit val ac = actorSystem
    implicit val lf = channelsLoggerFactory
    val nn = tag+"*"
    val n = api.makeTie(nn);
    if (logger.isTraceEnabled()) {
      logger.trace(s"created next tie ${nn}");
    }
    shutdownFuture.onSuccess{
      case _ => 
         if (logger.isTraceEnabled()) {
           logger.trace(s"success shutdown of channel ${tag}, start next");
         }
         n.start() 
    }
    n
  }
  
  /**
   * Tag, used for debug purposes.   
   */
  def tag: String 

  /*
   * Logback logger, used for this wiht this tag.  
   * We use logback instead slf4j to be able setup log levels and
   * add appenders to logger.
   */
  def logger: LogbackLogger
  
  def executionContext : ExecutionContext
  def actorSystem: ActorSystem
  def channelsLoggerFactory: ChannelsLoggerFactory
  
   
}