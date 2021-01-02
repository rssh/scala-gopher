package gopher.impl

import scala.util.Try

trait Writer[A] extends Expirable[(A,Try[Unit]=>Unit)]


class SimpleWriter[A](a:A, f: Try[Unit]=>Unit) extends Writer[A]:

  def canExpire: Boolean = false

  def isExpired: Boolean = false

  def capture(): Option[(A,Try[Unit]=>Unit)] = Some((a,f))

  def markUsed(): Unit = ()

  def markFree(): Unit = ()


