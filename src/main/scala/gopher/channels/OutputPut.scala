package gopher.channels

import scala.concurrent._
import akka.actor._

  class OutputPutAction[A](it: Iterator[A]) extends WriteAction[A]
  {
     override def apply(in: WriteActionInput[A]) : Option[Future[WriteActionOutput[A]]] =
     {
       val retval = if (it.hasNext) {
                         WriteActionOutput[A](Some(it.next),true)       
                    } else {
                         WriteActionOutput[A](None,false)
                    } 
       Some(Promise.successful(retval).future)
     }
  }


class OutputPut[API <: ChannelsAPI[API],-A](val ch: API#OChannel[A]) extends AnyVal {

  def put[API <: ChannelsAPI[API]](c:Iterable[A])(implicit api: ChannelsAPI[API], ec: ExecutionContext, as: ActorSystem): Unit =
  {
   val t = api.makeTie
                    //compiler bug.  TODO: write bug report
   t.addWriteAction(ch.asInstanceOf[API#OChannel[A]], new OutputPutAction(c.iterator))
  }


  
}