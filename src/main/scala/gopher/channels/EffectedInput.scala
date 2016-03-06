package gopher.channels

import gopher._
import gopher.util._
import scala.concurrent._

trait EffectedInput[A] extends Input[A] with Effected[Input[A]]


object EffectedInput
{
   def apply[A](in: Input[A], policy: ThreadingPolicy): EffectedInput[A] =
     policy match {
       case ThreadingPolicy.Single => new SinglethreadedEffectedInput(in)
       case ThreadingPolicy.Multi => new MultithreadedEffectedInput(in)
     }
}

class SinglethreadedEffectedInput[A](in:Input[A]) extends SinglethreadedEffected[Input[A]](in) 
                                                          with EffectedInput[A]
{

  def cbread[B](f: ContRead[A,B] => Option[ContRead.In[A] => Future[Continuated[B]]],ft: FlowTermination[B]): Unit = v.cbread(f,ft)

  def api: GopherAPI = v.api

}

class MultithreadedEffectedInput[A](in:Input[A]) extends MultithreadedEffected[Input[A]](in) 
                                                         with EffectedInput[A]
{

  def cbread[B](f: ContRead[A,B] => Option[ContRead.In[A] => Future[Continuated[B]]],ft: FlowTermination[B]): Unit = v.get().cbread(f,ft)

  def api: GopherAPI = v.get().api

}
