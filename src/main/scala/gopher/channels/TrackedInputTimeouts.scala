package gopher.channels

import scala.concurrent._
import scala.concurrent.duration._
import scala.util._
import gopher._


/**
 * Wrap `origin` input into input, which produce 'timeout' value into `timeouts` channel 
 * when reading from wrapped channel take more time than `timeout` .
 *
 *@see InputChannel.trackInputTimeouts
 */
class TrackedInputTimeouts[A](origin: Input[A], timeout: FiniteDuration)
{

  def pair: (Input[A],Input[FiniteDuration]) = (wrapped, timeouts)

  val timeouts = origin.api.makeChannel[FiniteDuration]()

  val wrapped = new Input[A] {

     def  cbread[B](f: ContRead[A,B] => Option[ContRead.In[A]=>Future[Continuated[B]]], ft: FlowTermination[B]): Unit =
     {
      val c = api.actorSystem.scheduler.scheduleOnce(timeout){
                                                   timeouts.awrite(timeout)   
                                                  }(api.executionContext)
      def fIn(cont: ContRead[A,B]):Option[ContRead.In[A]=>Future[Continuated[B]]] =
      {
        f(ContRead(f,this,ft)) map { f1 => 
            c.cancel()
            in => in match {
                     case ContRead.Skip => Future successful ContRead(f,this,ft)
                     case ContRead.ChannelClosed => 
                                                    timeouts.close()
                                                    f1(ContRead.ChannelClosed)
                     case x@_ => f1(x) 
                  }                       
        }
      }

      origin.cbread(fIn,ft)
     }

     def api = origin.api

  }


}

