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
   def apply(x:P[A], n:Int, api: GopherAPI): (IndexedSeq[P[A]],Option[ForeverSelectorBuilder])
}
  
class SharePortAdapter[P[_],A] extends PortAdapter[P,A]
{
   def apply(x:P[A], n:Int, api: GopherAPI): (IndexedSeq[P[A]],Option[ForeverSelectorBuilder]) 
      = ((1 to n) map (_ => x) ,None)
}

class DuplicatePortAdapter[A](buffLen: Int = 1) extends PortAdapter[Input,A]
{
   def apply(x:Input[A], n:Int, api: GopherAPI): (IndexedSeq[Input[A]],Option[ForeverSelectorBuilder]) 
      = {
       val upApi = api
       val newPorts = (1 to n) map (_ => api.makeChannel[A](buffLen))
       val selector = api.select.forever      
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
      (newPorts, Some(selector))
   }
}


class DistributePortAdapter[A](f: A=>Int, buffLen: Int = 1) extends PortAdapter[Input,A]
{
   def apply(x:Input[A], n:Int, api: GopherAPI): (IndexedSeq[Input[A]],Option[ForeverSelectorBuilder]) =
   {
       val newPorts = (1 to n) map (_ => api.makeChannel[A](buffLen))
       val selector = api.select.forever      
       selector.readingWithFlowTerminationAsync(x,
           (ec:ExecutionContext, ft: FlowTermination[Unit], a: A) => 
                                                    newPorts(f(a) % n).awrite(a).map(_ => ())(ec)
       )
       (newPorts, Some(selector))
   }
}


class AggregatePortAdapter[A](f: Seq[A]=>A, buffLen:Int = 1) extends PortAdapter[Output,A]
{

   def apply(x:Output[A], n:Int, api: GopherAPI): (IndexedSeq[Output[A]],Option[ForeverSelectorBuilder]) =
   {
       val newPorts = (1 to n) map (_ => api.makeChannel[A](buffLen))
       val selector = api.select.forever      
       selector.readingWithFlowTerminationAsync(newPorts(0),
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
       (newPorts,Some(selector))
   }

}

trait ReplicatedTransputer[T<:Transputer, Self] extends Transputer 
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
