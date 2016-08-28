package gopher.util

import java.util.concurrent.atomic._

trait Effected[T]
{

  def apply(f:T=>T): Unit

  @inline def <<=(f:T=>T): Unit = apply(f)

  def replace(x: T): Unit = apply(_ => x)

  @inline def :=(x:T): Unit = replace(x)

  protected def current: T
}


class SinglethreadedEffected[T](initValue:T) extends Effected[T]
{

  override def apply(f: T=>T): Unit =
  { v=f(v) }

  override def replace(x: T): Unit =
  { v=x }

  override def current = v

  protected[this] var v = initValue
}

class MultithreadedEffected[T](initValue:T) extends Effected[T]
{

  override def apply(f: T=>T): Unit =
  {
   setv(f(v.get))
  }

  override def replace(x:T)
  {
   setv(x)
  }

  protected[this] def setv(x: =>T):Unit =
  {
   var success = false;
   while(!success) {
    val prev = v.get()
    val next = x
    success = v.compareAndSet(prev,next)
   }
  }

  override def current = v.get

  protected[this] val v = new AtomicReference[T](initValue)
}


