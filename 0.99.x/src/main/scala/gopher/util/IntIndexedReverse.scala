package gopher.util



import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class CounterRecord[T](var value: T, var counter: Int = 0)

class IntIndexedCounterReverse[T <: AnyRef](n:Int) {

  val values = new Array[CounterRecord[T]](n)
  val backIndex = new mutable.WeakHashMap[T,CounterRecord[T]]()

  def put(i: Int, v:T):CounterRecord[T] =
  {
    var cr = values(i)
    if ((cr eq null) || !(cr.value eq v)) {
      backIndex.get(v) match {
        case None => cr = new CounterRecord[T](v,0)
                          backIndex.put(v,cr)
        case Some(backed) => cr = backed
      }
      values(i) = cr
    }
    cr
  }

  def get(i: Int): Option[CounterRecord[T]] =
    Option(values(i))

  /**
    * Search for index of v in T by reference.
    * @param v - value to search
    * @return index or -1 if not found
    */
  def  refIndexOf(v:T):Int =
  {
    var i=0
    var r = -1
    while(i < values.length && r == -1) {
      if (values(i).value eq v) {
        r = i
      }
      i += 1
    }
    r
  }

  def foreach(f:CounterRecord[T] => Unit): Unit =
  {
    var i=0
    while(i<values.length) {
      val current = values(i)
      if (!(current eq null)) {
        f(current)
      }
      i+=1
    }
  }

  def foreachIndex(f:(Int,CounterRecord[T])=>Unit):Unit =
  {
    var i = 0
    while(i < values.length) {
      val current = values(i)
      if (!(current eq null)) {
        f(i,current)
      }
      i += 1
    }
  }


  def getBackIndex(v:T):Option[CounterRecord[T]] =
    backIndex.get(v)

}
