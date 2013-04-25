package go.channels

import java.util.concurrent.{BlockingQueue => JBlockingQueue}
import akka.util._
import akka.actor._
import scala.concurrent._
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit


trait InputChannel[+A]
{
  
  channel =>

  def readBlocked: A 
  def readImmediatly: Option[A]
  def readTimeout(timeout: Duration) : Option[A]

  @inline def ? = readBlocked
  @inline def ?? (implicit timeout: Timeout) = readTimeout(timeout.duration)
  @inline def ?! = readImmediatly

  // TODO: check that it can extends AnyVal
  trait InputAsync
  {
     def read(implicit ec: ExecutionContext): Future[A] = Future(channel.readBlocked)
     def readTimeout(d: Duration)(implicit ec: ExecutionContext): Future[A] = 
        Future( channel.readTimeout(d) match {
                  case Some(x) => x
                  case None => throw new TimeoutException
                }
              )

     @inline def ?(implicit ec: ExecutionContext) = read

  }

  def async: InputAsync = new InputAsync() {}

  /**
   * add listener, which notifiew when new element is available
   * to get from queue.  Input channel must hold weak reference
   * to listener, which will be removed when listener has become
   * weakly unreachable.
   **/
  def addListener(f: A=> Boolean): Unit

}

