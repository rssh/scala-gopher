package gopher.channels

import gopher._
import gopher.util._
import scala.concurrent._

trait EffectedOutput[A] extends Effected[Output[A]] with Output[A]

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
{

  def  cbwrite[B](f: ContWrite[A,B] => Option[
                   (A,Future[Continuated[B]])
                  ],
                  ft: FlowTermination[B]): Unit = v.cbwrite(f,ft)


  def api: GopherAPI = v.api

}

class MultithreadedEffectedOutput[A](out:Output[A]) extends MultithreadedEffected[Output[A]](out)
                                                              with EffectedOutput[A]
{

  def  cbwrite[B](f: ContWrite[A,B] => Option[
                   (A,Future[Continuated[B]])
                  ],
                  ft: FlowTermination[B]): Unit = v.get().cbwrite(f,ft)


  def api: GopherAPI = v.get().api

}



