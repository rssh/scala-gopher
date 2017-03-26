package gopher.channels


import gopher._
import gopher.channels.ContRead.In
import gopher.util._

import scala.concurrent._

/*
trait EffectedChannel[A] extends Channel[A] with Effected[Channel[A]]
{
   thisEffectedChannel =>

   def asInput(): EffectedInput[A]
   def asOutput(): EffectedOutput[A]

   override val doneSignal: Input[Unit] = new Input[Unit] {
      override def api: GopherAPI = thisEffectedChannel.api

      override def cbread[B](f: (ContRead[Unit, B]) => Option[(In[Unit]) => Future[Continuated[B]]], ft: FlowTermination[B]): Unit =


   }

}


object EffectedChannel
{
   def apply[A](in: Channel[A], policy: ThreadingPolicy): EffectedChannel[A] =
     policy match {
       case ThreadingPolicy.Single => new SinglethreadedEffectedChannel(in)
       case ThreadingPolicy.Multi => new MultithreadedEffectedChannel(in)
     }
}


class SinglethreadedEffectedChannel[A](ch:Channel[A]) extends SinglethreadedEffected[Channel[A]](ch)
                                                          with EffectedChannel[A]
{

  def cbread[B](f: ContRead[A,B] => Option[ContRead.In[A] => Future[Continuated[B]]],ft: FlowTermination[B]): Unit = v.cbread(f,ft)

  def  cbwrite[B](f: ContWrite[A,B] => Option[
                   (A,Future[Continuated[B]])
                  ],
                  ft: FlowTermination[B]): Unit = v.cbwrite(f,ft)

  def close() = v.close()

  def asInput() = api.makeEffectedInput(v, ThreadingPolicy.Single)

  def asOutput() = api.makeEffectedOutput(v, ThreadingPolicy.Single)

  def api: GopherAPI = v.api

  //override def filter(p:A=>Boolean):Channel[A] = new SinglethreadedEffectedChannel(v.filter(p))

}

class MultithreadedEffectedChannel[A](ch:Channel[A]) extends MultithreadedEffected[Channel[A]](ch)
                                                         with EffectedChannel[A]
{

  def cbread[B](f: ContRead[A,B] => Option[ContRead.In[A] => Future[Continuated[B]]],ft: FlowTermination[B]): Unit = v.get().cbread(f,ft)

  def  cbwrite[B](f: ContWrite[A,B] => Option[
                   (A,Future[Continuated[B]])
                  ],
                  ft: FlowTermination[B]): Unit = v.get().cbwrite(f,ft)

  def close() = v.get().close()

  def asInput() = api.makeEffectedInput(v.get(), ThreadingPolicy.Multi)

  def asOutput() = api.makeEffectedOutput(v.get(), ThreadingPolicy.Multi)


  def api: GopherAPI = v.get().api

}

*/
