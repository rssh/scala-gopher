package gopher.channels

import scala.concurrent._
import akka.actor._

  class OutputPutAction[A](it: Iterator[A]) extends WriteAction[A]
  {
     override def apply(in: WriteActionInput[A]) : Option[Future[WriteActionOutput[A]]] =
     {
       val retval = if (it.hasNext) {
                         val next = it.next;
                         System.err.println("put: "+next)
                         WriteActionOutput[A](Some(next),true)       
                    } else {
                         WriteActionOutput[A](None,false)
                    } 
       Some(Promise.successful(retval).future)
     }
  }


trait OutputChannelOps[API <: ChannelsAPI[API],-A] extends ChannelBase[API]  
{

  this: API#OChannel[A] =>
  
  def put(c:Iterable[A]) =
  {
   makeTie.addWriteAction(this, new OutputPutAction(c.iterator))
  }

}