package gopher.impl

import gopher._
import scala.concurrent._
import scala.concurrent.duration._
import scala.util._
import java.util.concurrent.TimeUnit


class SimpleWriterWithExpireTime[A](a:A, f: Try[Unit] => Unit, expireTimeMillis: Long) extends Writer[A]: 

  def canExpire: Boolean = true

  def isExpired: Boolean = 
    //TODO: way to mock current time
    System.currentTimeMillis >= expireTimeMillis

  def capture(): Option[(A,Try[Unit]=>Unit)] = Some((a,f))

  def markUsed(): Unit = ()

  def markFree(): Unit = ()

 // TODO: pass time source 
class NesteWriterWithExpireTime[A](nested: Writer[A], expireTimeMillis: Long) extends Writer[A]:

  def canExpire: Boolean = true
  
  def isExpired: Boolean =
    (System.currentTimeMillis >= expireTimeMillis) ||  nested.isExpired

  def capture(): Option[(A,Try[Unit]=>Unit)] = 
    if (isExpired) None else nested.capture()

  def markUsed(): Unit = nested.markUsed()

  def markFree(): Unit = nested.markFree()

class NestedWriterWithExpireTimeThrowing[F[_],A](nested: Writer[A], expireTimeMillis: Long, gopherApi: Gopher[F]) extends Writer[A]:

  val scheduledThrow = gopherApi.time.schedule(
    () => checkExpire(),
    FiniteDuration(expireTimeMillis - gopherApi.time.now().toMillis, TimeUnit.MILLISECONDS)   
  )

  def canExpire: Boolean = true
  
  def isExpired: Boolean =
    (gopherApi.time.now().toMillis >= expireTimeMillis) ||  nested.isExpired

  def capture(): Option[(A,Try[Unit]=>Unit)] = 
    nested.capture()

  def markUsed(): Unit = 
    scheduledThrow.cancel()
    nested.markUsed()

  def markFree(): Unit = 
    nested.markFree()
    checkExpire()

  def checkExpire(): Unit =
    if (gopherApi.time.now().toMillis > expireTimeMillis) then
      if (!nested.isExpired) then
        nested.capture() match
          case Some((a,f)) => 
            nested.markUsed()
            try
              f(Failure(new TimeoutException()))
            catch
              case ex: Throwable =>
                ex.printStackTrace()
          case None =>
            // none, will be colled after markFree is needed.
  




      