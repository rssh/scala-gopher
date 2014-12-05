package gopher.transputers

import gopher._
import gopher.channels._
import gopher.util._
import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context
import scala.reflect.api._
import scala.concurrent._
import scala.annotation._
import scala.language.higherKinds
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

   protected def replicatePorts():IndexedSeq[ForeverSelectorBuilder=>Unit]

   protected final def formChilds(selectorFuns:IndexedSeq[ForeverSelectorBuilder=>Unit]):Unit = {
         childs = (selectorFuns map(new SelectorRunner(_))) ++ replicatedInstances
         for(x <- childs) x.parent = Some(this)
   }

}




object PortAdapters
{
 
 implicit class DistributeInput[G <: ReplicatedTransputer[_,_], A](in: G#InPortWithAdapter[A])
 {
   def distribute(f: A=>Int): G = 
        {  in.adapter = new DistributePortAdapter(f)
           in.owner.asInstanceOf[G]
        }
 }


 implicit class ShareInput[G <: ReplicatedTransputer[_,_],A](in: G#InPortWithAdapter[A])
 {
   def share(): G =
        { in.adapter = new SharePortAdapter[Input,A]()
          in.owner.asInstanceOf[G]
        }
 }


 implicit class ShareOutput[G <: ReplicatedTransputer[_,_], A](out: G#OutPortWithAdapter[A])
 {
   def share(): G = 
        { out.adapter = new SharePortAdapter[Output,A] 
          out.owner.asInstanceOf[G]
        }
 }


 implicit class DuplicateInput[G <: ReplicatedTransputer[_,_],A](in: G#InPortWithAdapter[A])
 {
   def duplicate(): G = 
        { in.adapter = new DuplicatePortAdapter[A] 
          in.owner.asInstanceOf[G]
        }
 }



}



object Replicate
{

  /**
   * macro for GopherAPI.replicate
   */
  def replicateImpl[T<:Transputer:c.WeakTypeTag](c:Context)(n:c.Expr[Int]):c.Expr[Transputer] =
  {
    import c.universe._

    def portDefs[P:TypeTag](portWithAdapterName:String,portConstructorName:String):List[Tree] =
    {
      val portWithAdapterType = TypeName(portWithAdapterName)
      val portConstructor = TermName(portConstructorName)
      val ports = ReflectUtil.retrieveValSymbols[P](c.universe)(weakTypeOf[T])
      for(p <- ports) yield {
        val getter = p.getter.asMethod
        if (getter.returnType.typeArgs.length!=1) {
          c.abort(p.pos, "assumed {In|Out}Port[A], have type ${getter.returnType} with typeArgs length other then 1")
        }
        val ta = getter.returnType.typeArgs.head
        val name = TermName(getter.name.toString)
        q"val ${name}: ${portWithAdapterType}[${ta}] = new ${portWithAdapterType}[${ta}](${portConstructor}().v)"
      }
    }

    def replicatePort(p:TermName):Tree=
     q"""{ val (replicatedPorts,optSelectorFun) = ${p}.adapter(${p}.v,n,api)
           for((r,e) <- (replicatedPorts zip replicatedInstances)) {
              e.${p}.connect(r)
           }
           selectorFuns = selectorFuns ++ optSelectorFun
         }
      """

    def retrieveValNames[P:TypeTag]:List[TermName] = 
      ReflectUtil.retrieveValSymbols[P](c.universe)(weakTypeOf[T]) map (x=>TermName(x.getter.name.toString))

    def replicatePorts():List[Tree] =
    {
      (retrieveValNames[Transputer#InPort[_]] ++ retrieveValNames[Transputer#OutPort[_]]) map (replicatePort(_))
    }


    val className  = TypeName(c.freshName("Replicated"+weakTypeOf[T].typeSymbol.name))
    var stats = List[c.Tree]() ++ ( 
                   portDefs[Transputer#InPort[_]]("InPortWithAdapter","InPort")
                   ++
                   portDefs[Transputer#OutPort[_]]("OutPortWithAdapter","OutPort")
                )

    val retval = c.Expr[Transputer](q"""
     {
      class ${className}(api:GopherAPI,n:Int) extends ReplicatedTransputer[${weakTypeOf[T]},${className}](api,n)
      {
       type Self = ${className}
 
       def init(): Unit =
       {
         replicatedInstances = (1 to n) map (i => {
              val x = api.makeTransputer[${weakTypeOf[T]}]
              x.replicaNumber = i
              x
         })
         formChilds(replicatePorts)
       }

       def replicatePorts():IndexedSeq[ForeverSelectorBuilder=>Unit] =
       {
         var selectorFuns = IndexedSeq[ForeverSelectorBuilder=>Unit]()

         ..${replicatePorts()}

         selectorFuns
       }


       ..${stats}

      }
      new ${className}(${c.prefix},${n.tree})
     }
    """
    )  
    retval
  }


}
