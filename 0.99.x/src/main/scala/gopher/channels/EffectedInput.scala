package gopher.channels

import gopher._
import gopher.util._
import scala.concurrent._

import scala.collection.mutable.{HashSet => MutableHashSet}
import java.util.concurrent.{ConcurrentHashMap=>JavaConcurrentHashMap}

/*
trait EffectedInput[A] extends Input[A] with Effected[Input[A]]
{

  def cbread[B](f: ContRead[A,B] => Option[ContRead.In[A] => Future[Continuated[B]]],ft: FlowTermination[B]): Unit = {
    val sv = current
    sv.cbread((cr:ContRead[A,B]) => if (sv==current) f(cr.copy(channel=this)) else None, ft)
  }

  def api: GopherAPI = current.api

}


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

class MultithreadedEffectedInput[A](in:Input[A]) extends MultithreadedEffected[Input[A]](in) 
                                                         with EffectedInput[A]

*/