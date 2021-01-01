package gopher.impl

import scala.util.Try

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



      