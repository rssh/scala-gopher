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

  def capture(): Expirable.Capture[(A,Try[Unit]=>Unit)] = Expirable.Capture.Ready((a,f))

  def markUsed(): Unit = ()

  def markFree(): Unit = ()

 // TODO: pass time source 
class NesteWriterWithExpireTime[A](nested: Writer[A], expireTimeMillis: Long) extends Writer[A]:

  def canExpire: Boolean = true
  
  def isExpired: Boolean =
    (System.currentTimeMillis >= expireTimeMillis) ||  nested.isExpired

  def capture(): Expirable.Capture[(A,Try[Unit]=>Unit)] = 
    if (isExpired) Expirable.Capture.Expired else nested.capture()

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

  def capture(): Expirable.Capture[(A,Try[Unit]=>Unit)] = 
    if (gopherApi.time.now().toMillis > expireTimeMillis) then
      Expirable.Capture.Expired
    else
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
          case Expirable.Capture.Ready((a,f)) => 
            nested.markUsed()
            try
              f(Failure(new TimeoutException()))
            catch
              case ex: Throwable =>
                ex.printStackTrace()
          case Expirable.Capture.WaitChangeComplete =>
            gopherApi.time.schedule(
              () => checkExpire(),
              FiniteDuration(100, TimeUnit.MILLISECONDS)   )
          case Expirable.Capture.Expired =>
            // none, will be colled after markFree is needed.
  




      