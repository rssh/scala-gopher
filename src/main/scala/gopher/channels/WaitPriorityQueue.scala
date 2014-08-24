package gopher.channels

import scala.concurrent._

case class WaitRecord[A](priority:Int, value: A)


class WaitPriorityQueue
{
    import java.util.{PriorityQueue=>JPriorityQueue}

    def  put[A](x:Continuated[A], priority: Int): Unit =
    {
      queue add WaitRecord(priority, x)
    }

    def  take[A]: Option[WaitRecord[Continuated[A]]] = 
       Option(queue poll) map ( _.asInstanceOf[WaitRecord[Continuated[A]]])
      

    def  isEmpty: Boolean = queue.isEmpty
    def  nonEmpty: Boolean = ! queue.isEmpty

    object WaitRecordOrdering extends Ordering[WaitRecord[_]]
    {
       override def compare(x: WaitRecord[_], y: WaitRecord[_]) = x.priority compare y.priority 
    }

    val queue = new JPriorityQueue[WaitRecord[_]](10, WaitRecordOrdering);
     

}
