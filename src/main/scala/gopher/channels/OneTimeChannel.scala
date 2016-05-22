package gopher.channels

import scala.concurrent._
import gopher._
import java.util.concurrent.atomic._

/**
 * channel, in which only one message can be written,
 * after which it is automatically closed
 *
 * Writer is not waiting for reader to start.   
 */
class OneTimeChannel[T](override val api:GopherAPI) extends Channel[T]
{
  private[this] val p = Promise[T]()
  private[this] val readed = new AtomicBoolean(false)

  def future = p.future
  def promise = p

  def cbread[B](f: ContRead[T,B] => Option[ContRead.In[T] => Future[Continuated[B]]],ft: FlowTermination[B]): Unit = 
  {
   p.future.foreach{ a =>
       f(ContRead(f,this,ft)) foreach { g =>
            if (readed.compareAndSet(false,true)) {
                api.continue(g(ContRead.Value(a)),ft)
            } else{
                api.continue(g(ContRead.Skip),ft)
            }
       }
   }(api.executionContext)
  }
   
  def cbwrite[B](f: ContWrite[T,B] => Option[(T, Future[Continuated[B]])],ft: FlowTermination[B]): Unit = 
  {
    if (p.isCompleted) {
       ft.doThrow(new ChannelClosedException())
    } else {
       f(ContWrite(f,this,ft)) foreach { case (a, next) =>
           if (!p.trySuccess(a)) {
             ft.doThrow(throw new ChannelClosedException())
           }
           api.continue(next,ft)
       }
    }
  }


  def close(): Unit = 
       p failure new ChannelClosedException()

}

object OneTimeChannel
{

  def apply[A]()(implicit api:GopherAPI): OneTimeChannel[A] =
    new OneTimeChannel[A](api)

}
