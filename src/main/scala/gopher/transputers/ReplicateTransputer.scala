package gopher.transputers

import gopher._
import gopher.channels._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import scala.concurrent._
import scala.annotation._
import async.Async._

import scala.collection.mutable.ArraySeq

trait PortAdapter[P[_],A]
{
   def apply(x:P[A], n:Int, api: GopherAPI): (IndexedSeq[P[A]],Option[ForeverSelectorBuilder=>Unit])
}
  
class SharePortAdapter[P[_],A] extends PortAdapter[P,A]
{
   def apply(x:P[A], n:Int, api: GopherAPI): (IndexedSeq[P[A]],Option[ForeverSelectorBuilder=>Unit]) 
      = ((1 to n) map (_ => x) ,None)
}

class DuplicatePortAdapter[A](buffLen: Int = 1) extends PortAdapter[Input,A]
{
   def apply(x:Input[A], n:Int, api: GopherAPI): (IndexedSeq[Input[A]],Option[ForeverSelectorBuilder=>Unit]) 
      = {
       val upApi = api
       val newPorts = (1 to n) map (_ => api.makeChannel[A](buffLen))
       def f(selector:ForeverSelectorBuilder): Unit =
         selector.readingWithFlowTerminationAsync(x,
           (ec:ExecutionContext, ft: FlowTermination[Unit], a: A) => async{
               var i = 0
               var fl:List[Future[A]]=Nil
               while(i<n) {
                  val f = newPorts(i).awrite(a)
                  i += 1
                  fl = f::fl
               }
               while(fl != Nil) {
                  val f = fl.head
                  fl = fl.tail
                  await(f)
               }
         }(ec)
      )
      (newPorts, Some(f))
   }
}


class DistributePortAdapter[A](f: A=>Int, buffLen: Int = 1) extends PortAdapter[Input,A]
{
   def apply(x:Input[A], n:Int, api: GopherAPI): (IndexedSeq[Input[A]],Option[ForeverSelectorBuilder=>Unit]) =
   {
       val newPorts = (1 to n) map (_ => api.makeChannel[A](buffLen))
       val sf: (ForeverSelectorBuilder=>Unit) = _.readingWithFlowTerminationAsync(x,
           (ec:ExecutionContext, ft: FlowTermination[Unit], a: A) => 
                                                    newPorts(f(a) % n).awrite(a).map(_ => ())(ec)
       )
       (newPorts, Some(sf))
   }
}


class AggregatePortAdapter[A](f: Seq[A]=>A, buffLen:Int = 1) extends PortAdapter[Output,A]
{

   def apply(x:Output[A], n:Int, api: GopherAPI): (IndexedSeq[Output[A]],Option[ForeverSelectorBuilder=>Unit]) =
   {
       val newPorts = (1 to n) map (_ => api.makeChannel[A](buffLen))
       val sf: (ForeverSelectorBuilder=>Unit) = _.readingWithFlowTerminationAsync(newPorts(0),
           (ec:ExecutionContext, ft: FlowTermination[Unit], a: A) => async{
              val data = new ArraySeq[A](n)
              data(0) = a
              val i=1
              while(i<n) {
                data(i)=await(newPorts(i).aread)
              }
              await(x.awrite(f(data)))
              ()
           }(ec)
       )
       (newPorts,Some(sf))
   }

}


abstract class ReplicatedTransputer[T<:Transputer, Self](api: GopherAPI, n: Int) extends ParTransputer(api,List())
{

   thisReplicatedTransputer: Self =>

   class InPortWithAdapter[A](in:Input[A]) extends InPort[A](in)
   {
     var adapter: PortAdapter[Input, A] = new SharePortAdapter[Input,A]
     def owner: Self = thisReplicatedTransputer
   }

   class OutPortWithAdapter[A](out:Output[A]) extends OutPort[A](out)
   {
     var adapter: PortAdapter[Output, A] = new SharePortAdapter[Output,A]
     def owner: Self = thisReplicatedTransputer
   }

   
   class SelectorRunner(configFun: ForeverSelectorBuilder => Unit ) extends SelectTransputer
   {

     selectorInit = ()=>configFun(this)
     selectorInit()

     def api = thisReplicatedTransputer.api
     def recoverFactory = () => new SelectorRunner(configFun)
   }

   def init(): Unit 


   override def onStart():Unit=
       { init() }

   override def onRestart(prev:Transputer):Unit=
       { init() }



   def replicated: Seq[T] 
                 = replicatedInstances

   protected var replicatedInstances: Seq[T] = Seq()



}




object PortAdapters
{
 
 implicit class DistributeInput[T<:Transputer, G <: ReplicatedTransputer[T,G],A](in: ReplicatedTransputer[T,G]#InPortWithAdapter[A]) 
 {
   def distribute(f: A=>Int): G = 
        {  in.adapter = new DistributePortAdapter(f)
           in.owner
        }
 }


 implicit class ShareInput[T <: Transputer, G <: ReplicatedTransputer[T,G],A](in: ReplicatedTransputer[T,G]#InPortWithAdapter[A])
 {
   def share(): G =
        { in.adapter = new SharePortAdapter[Input,A]()
          in.owner
        }
 }


 implicit class ShareOutput[T<:Transputer,G <: ReplicatedTransputer[T,G], A](out: ReplicatedTransputer[T,G]#OutPortWithAdapter[A])
 {
   def share(): G = 
        { out.adapter = new SharePortAdapter[Output,A] 
          out.owner
        }
 }





}



/**

Replicate[TestTransputer](  _.in.distribute( _ % 2)
                             .out.share
                         )

   }(n)
 =>

Replicate[TestTransputer](  _.inTransform(q"TestTransputer.in".sym, new DistributePortAdapter(f)).toT.
                              outTransform(q"TestTransputer.out".sym, new SharePortAdapter())   )

*/


object Replicate
{

  def apply[T<:Transputer](cnSpec: (T => T)): Transputer = macro applyImpl[T]

  def applyImpl[T:c.WeakTypeTag](c:Context)(cnSpec:c.Expr[T=>T]):c.Expr[Transputer] =
  {  
     Console.println(s"replicate for ${c.weakTypeOf[T]} is called") 
     Console.println(s"cnSpec: ${cnSpec}") 
     ???
  }
}
