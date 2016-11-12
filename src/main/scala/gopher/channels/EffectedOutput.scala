package gopher.channels

import gopher._
import gopher.util._
import scala.concurrent._

trait EffectedOutput[A] extends Effected[Output[A]] with Output[A]
{

  def  cbwrite[B](f: ContWrite[A,B] => Option[
                   (A,Future[Continuated[B]])
                  ],
                  ft: FlowTermination[B]): Unit = {
    val sv = current
    sv.cbwrite[B](cw => if (current eq sv) f(cw.copy(channel=this)) else None,ft)
  }

  def api: GopherAPI = current.api

}

object EffectedOutput
{
   def apply[A](in: Output[A], policy: ThreadingPolicy): EffectedOutput[A] =
     policy match {
       case ThreadingPolicy.Single => new SinglethreadedEffectedOutput(in)
       case ThreadingPolicy.Multi => new MultithreadedEffectedOutput(in)
     }
}

class SinglethreadedEffectedOutput[A](out:Output[A]) extends SinglethreadedEffected[Output[A]](out)
                                                              with EffectedOutput[A]

class MultithreadedEffectedOutput[A](out:Output[A]) extends MultithreadedEffected[Output[A]](out)
                                                              with EffectedOutput[A]



