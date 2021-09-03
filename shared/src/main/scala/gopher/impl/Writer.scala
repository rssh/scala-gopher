package gopher.impl

import scala.util.Try

trait Writer[A] extends Expirable[(A,Try[Unit]=>Unit)]


class SimpleWriter[A](a:A, f: Try[Unit]=>Unit) extends Writer[A]:

  def canExpire: Boolean = false

  def isExpired: Boolean = false

  def capture(): Expirable.Capture[(A,Try[Unit]=>Unit)] = Expirable.Capture.Ready((a,f))

  def markUsed(): Unit = ()

  def markFree(): Unit = ()


