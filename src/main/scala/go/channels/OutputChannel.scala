package go.channels

import java.util.concurrent.{BlockingQueue => JBlockingQueue}
import akka.util._
import akka.actor._
import scala.concurrent._
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit


trait OutputChannel[A]
{

  channel =>

  def writeBlocked(x:A):Unit 

  def writeImmediatly(x:A): Boolean 

  def writeTimeout(x:A, timeout: Duration): Boolean

  @inline def   ! (x:A) = writeBlocked(x)
  @inline def  !! (x:A) = writeImmediatly(x)
  @inline def <~  (x:A) = writeBlocked(x)
  @inline def <~! (x:A) = writeImmediatly(x)
  @inline def <~? (x:A)(implicit timeout: Timeout) = writeTimeout(x,timeout.duration)

  trait OutputAsync
  {
     def write(x:A)(implicit ec:ExecutionContext): Future[Unit] = Future(channel.writeBlocked(x))

     @inline def <~ (x:A)(implicit ec: ExecutionContext) = write(x) 
  }

  def async: OutputAsync = new OutputAsync() {}

  def addListener(f: () => Option[A] ): Unit

  
}


