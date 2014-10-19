package gopher.channels

import scala.concurrent._
import scala.concurrent.duration._
import scala.util._
import gopher._


/**
 *
 *
 */
class InputWithTimeout[A](origin: Input[A], timeout: FiniteDuration) extends Input[Either[FiniteDuration,A]]
{


  /**
   * apply f, when input will be ready and send result to API processor
   */
  def  cbread[B](f:
            ContRead[Either[FiniteDuration,A],B]=>Option[
                    ContRead.In[Either[FiniteDuration,A]]=>Future[Continuated[B]]
            ], 
            ft: FlowTermination[B]): Unit =
  {
   val thisCont = ContRead(f,this,ft)
   val p = Promise[Boolean]()
   val cancellable = api.actorSystem.scheduler.scheduleOnce(timeout){ () => 
                           val b = p trySuccess false 
                           if (b || p.isCompleted && p.future.value.get==Success(false)) {
                            f(thisCont) match {
                               case Some(f1) => api.continue(f1(ContRead.Value(Left(timeout))),ft)
                               case None => // TODO: think - may be set timeout for this reader and
                                             //  later fire one when [if] reader will be returned ?
                                            //  (keep something like WeakIdentityHashSet for f)
                            }
                           }
                    }(api.executionContext)
   def fa(cont:ContRead[A,B]):Option[ContRead.In[A]=>Future[Continuated[B]]] =
   {
     if (p.isCompleted) {
        None
     } else {
        f(thisCont) map { f1 =>
           if (p.isCompleted) {
             in => f1(ContRead.Skip)
           } else {
            if (p trySuccess true) {
               cancellable.cancel
               in => in match {
                       case ContRead.Value(a) => f1(ContRead.Value(Right(a)))
                       case ContRead.Skip => Future successful thisCont
                       case ContRead.ChannelClosed => f1(ContRead.ChannelClosed)
                       case failure@ContRead.Failure(ex) => f1(failure)
               }
            } else { // timeout was fired [during call of f(thisCont) ]
               in => Future successful Never
            }
           }
        }
   }
  }

  origin.cbread(fa,ft)
 }

 def api = origin.api

}
