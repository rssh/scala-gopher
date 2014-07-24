package gopher.channels

import scala.concurrent._
import akka.actor._

trait TieJoin {
  
  def processExclusive[A](f: => Future[A], whenLocked: =>A): Future[A]
  
  implicit def executionContext: ExecutionContext
  
  implicit def actorSystem: ActorSystem
  
  def shutdown(): Unit
  
}

trait TieReadJoin[+A] extends TieJoin {

  def putNext(action: ReadAction[A]): Unit
  
}

trait TieWriteJoin[A] extends TieJoin {
  
  def putNext(action: WriteAction[A]): Unit
 
  
}