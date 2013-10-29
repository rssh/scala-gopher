package gopher.channels

import akka.util._
import akka.actor._
import scala.concurrent._
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit


trait OutputChannel[-A] 
{

  outputChannel =>

  type OutputElement = A

  def writeBlocked(x:A): Unit

  def writeImmediatly(x:A): Boolean 

  def writeBlockedTimeout(x:A, timeout: FiniteDuration): Boolean
  
  def writeAsync(x:A): Future[Unit]
  
  def writeAsyncTimeout(x:A, timeout: FiniteDuration): Future[Boolean]

  /**
   * synonym for writeBlocked
   */
  @inline def   ! (x:A) = writeBlocked(x)
  
  /**
   * synonym for writeImmediatly
   */
  @inline def  !! (x:A) = writeImmediatly(x)
  
  /**
   * synonym for writeBlocked
   */
  @inline def <~  (x:A) = writeBlocked(x)
  
  /**
   * synonym for writeImmediatly
   */
  @inline def <~! (x:A) = writeImmediatly(x)
  
  
  /**
   * synonym for writeBlockedTimeout
   */
  @inline def <~? (x:A)(implicit timeout: Timeout) = writeBlockedTimeout(x,timeout.duration)

  /**
   * synonym for writeAsync
   */
  @inline def <~* (x:A) = writeAsync(x)

  
  trait OutputAsync
  {
     def write(x:A): Future[Unit] = outputChannel.writeAsync(x)

     @inline def <~ (x:A) = write(x) 
  }

  def async: OutputAsync = new OutputAsync() {}
  
    
  
}


