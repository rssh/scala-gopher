package gopher.channels

import scala.concurrent._
import scala.concurrent.duration._
import scala.async.Async._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import gopher._

/**
 * 
 *
 */
class OutputWithTimeouts[A](origin: Output[A], timeout: FiniteDuration)
{


  def pair:(Output[A],Input[FiniteDuration]) = (wrapped, timeouts)

  val timeouts: IOChannel[FiniteDuration] = api.makeChannel[FiniteDuration]()

  val wrapped: Output[A] = new Output[A] {

      def  cbwrite[B](f: ContWrite[A,B] => Option[(A,Future[Continuated[B]])],
                      ft: FlowTermination[B]): Unit = 
      {
       val c = api.actorSystem.scheduler.scheduleOnce(timeout){
                                                   timeouts.awrite(timeout)
                                                  }(api.executionContext)
       def fIn(cont: ContWrite[A,B]):Option[(A,Future[Continuated[B]])] =
       {
        try {
          f(ContWrite(f,this,ft)) map { case (a,next) =>
               c.cancel()
               (a,next)
          }
        } catch {
          case ex:ChannelClosedException => timeouts.close()
                                            throw ex
        }
       }

       origin.cbwrite(fIn,ft)
      }

      def api = origin.api

 }

 def api = origin.api

}

