package gopher.util

import java.util.concurrent.atomic._

trait Effected[T]
{

  def apply(f:T=>T): Unit

  @inline def <<=(f:T=>T): Unit = apply(f)

  def replace(x: T): Unit = apply( _ => x)

  @inline def :=(x:T): Unit = replace(x)

}


class SinglethreadedEffected[T](initValue:T) extends Effected[T]
{

  override def apply(f: T=>T)
  { v=f(v) }

  protected[this] var v = initValue
}

class MultithreadedEffected[T](initValue:T) extends Effected[T]
{

  override def apply(f: T=>T): Unit =
  {
   var success = false;
   while(!success) {
    val prev = v.get()
    val next = f(prev)
    success = v.compareAndSet(prev,next)
   }
  }

  protected[this] val v = new AtomicReference[T](initValue)
}


