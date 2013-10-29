package gopher.channels

import java.util.concurrent.{BlockingQueue => JBlockingQueue}
import akka.util._
import akka.actor._
import scala.concurrent._
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import gopher.channels.naive.Activable


trait InputChannel[+A] 
{
  
  inputChannel =>

  type InputElement = A;

  def readImmediatly: Option[A]
  def readBlocked: A 
  def readBlockedTimeout(timeout: FiniteDuration) : Option[A]
  def readAsync: Future[A]
  def readAsyncTimeout(timeout: FiniteDuration) : Future[Option[A]]
  
  /**
   * synonym for readBlocked
   */
  @inline def ? = readBlocked
  
  /**
   * synonym for readBlockedTimeout
   */
  @inline def ?? (implicit timeout: Timeout) = readBlockedTimeout(timeout.duration)
  
  /**
   * synonym for readImmediatly
   */
  @inline def ?! = readImmediatly

  /**
   * synonym for readAsync
   */
  @inline def ?* = readAsync
    
  
  
  // sugar
  trait InputAsync
  {
     @inline
     def read: Future[A] = inputChannel.readAsync
     
     @inline
     def readTimeout(d: FiniteDuration): Future[Option[A]] = inputChannel.readAsyncTimeout(d)
                     
     @inline def ? = read

  }

  
  def async: InputAsync = new InputAsync() {}

  //TODO:
  //def readWhile[C](p: A => Boolean)(implicit val CanBuildFrom[C])

  /**
   * pass all output, which can be readed from this channel, to given actor.
   */
  //TODO: enable.
 // def bindRead(actor: ActorRef): Unit 
  
  

}


