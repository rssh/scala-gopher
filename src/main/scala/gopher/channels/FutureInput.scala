package gopher.channels

import scala.concurrent._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import scala.util._
import java.util.concurrent.ConcurrentLinkedQueue
import gopher._
 
/**
 * Future[A], represented as input which can produce a value when will be completed.
 * Can be obtained from gopherApi.
 *
 *{{{
 *  import gopherApi._
 *
 *  val myInput = futureInput(future)
 *  select.forever{
 *     case x: myInput => Console.println(s"we receive value from future: \${x}")
 *                           implicitly[FlowTermination[Unit]].doExit(())
 *     case x: myChannel => Console.println(s"value from channel: \${x}")
 *  }
 *}}}
 *
 */
class FutureInput[A](future: Future[A], override val api: GopherAPI) extends Input[A]
{

  def  cbread[B](f: (ContRead[A,B] => (Option[(()=>A) => Future[Continuated[B]]])), flwt: FlowTermination[B] ): Unit =
  {
   future.onComplete{  r => 
                       for (f1 <- f(ContRead(f,this,flwt))) {
                          if (closed) 
                            f1(() => throw new ChannelClosedException())
                          else {
                            closed = true
                            r match {
                             case Success(x) => f1(()=>x)
                             case Failure(ex) => f1(()=>throw ex)
                            }
                          }
                       }
                    }(api.executionContext)
  }

  def input: Input[A] = this

  @volatile private[this] var closed: Boolean = false

}


