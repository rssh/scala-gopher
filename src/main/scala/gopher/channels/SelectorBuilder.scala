package gopher.channels

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import gopher._
import scala.concurrent._
import scala.annotation.unchecked._

class SelectorBuilder[A](api: GopherAPI)
{


   def onRead[E](ch:Input[E])(arg: ReadSelectorArgument[E,A]): this.type =
   {
     selector.addReader(ch,arg.normalizedFun)
     this
   }

   def onWrite[E](ch:Output[E])(arg: WriteSelectorArgument[E,A]): this.type =
   {
     selector.addWriter(ch,arg.normalizedFun)
     this
   }

   def onIdle(arg: SkipSelectorArgument[A]): this.type =
   {
     selector.addIdleSkip(arg.normalizedFun)
     this
   }


   def go: Future[A] = selector.run

   implicit def ec: ExecutionContext = api.executionContext

   val selector=new Selector[A](api)

}

object SelectorBuilder
{

   def readingImpl[A,B,S](c:Context)(ch:c.Expr[Input[A]])(f:c.Expr[A=>B]):c.Expr[S] =
   {
      import c.universe._
      f.tree match {
         case Function(valdefs, body) => 
               buildAsyncCall[S](c)(valdefs,body, 
                                { (nvaldefs, nbody) =>
                                 q"""${c.prefix}.readingWithFlowTerminationAsync(${ch},
                                       ${Function(nvaldefs,nbody)}
                                      )
                                  """
                                })
         case _ => c.abort(c.enclosingPosition,"argument of reading.apply must be function")
      }
   }

   def writingImpl[A,T,S](c:Context)(ch:c.Expr[Output[A]],x:c.Expr[A])(body:c.Expr[T]):c.Expr[S] =
   {
     import c.universe._
     buildAsyncCall(c)(Nil,body.tree,
                   { (nvaldefs, nbody) =>
                     q"""${c.prefix}.writingWithFlowTerminationAsync(${ch},${x},
                             ${Function(nvaldefs,nbody)}
                       )
                     """
                   })
   }

   def transformDelayedMacroses(c:Context)(block:c.Tree):c.Tree =
   {
     import c.universe._
     val transformer = new Transformer {
        override def transform(tree:Tree): Tree =
          tree match {
             case Apply(TypeApply(Select(obj,TermName("implicitly")),List(objType)), args) =>
                    // unresolve implicit references of specific type
                    if (obj.tpe =:= typeOf[Predef.type] &&
                        objType.tpe <:< typeOf[FlowTermination[Nothing]]
                        ) {
                       TypeApply(Select(obj,TermName("implicitly")),List(objType))
                    } else {
                       super.transform(tree)
                    }
             case Apply(TypeApply(Select(obj,member),objType), args) =>
                    if (obj.tpe =:= typeOf[CurrentFlowTermination.type] ) {
                       member match {
                          case TermName("exit") => 
                                 Apply(TypeApply(Select(obj,TermName("exitDelayed")),objType), args) 
                          case _ => super.transform(tree)
                       }
                    } else {
                       super.transform(tree)
                    }
             case Apply(Select(obj,member), args) =>
                    if (obj.tpe =:= typeOf[CurrentFlowTermination.type] ) {
                       member match {
                          case TermName("exit") => 
                                   Apply(Select(obj,TermName("exitDelayed")),args)
                          case _ => super.transform(tree)
                       }
                    } else {
                       super.transform(tree)
                    }
             case _ => 
                    super.transform(tree)
          }
     }
     transformer.transform(block)
   }

   def buildAsyncCall[S](c:Context)(valdefs: List[c.universe.ValDef], body: c.Tree,
                                    lastFun: (List[c.universe.ValDef], c.Tree) => c.Tree): c.Expr[S] =
   {
     import c.universe._
     val Seq(ft, ft1, ec, ec1) = Seq("ft","ft","ec","ec1") map (x => TermName(c.freshName(x)))
     val ftParam = ValDef(Modifiers(Flag.PARAM),ft,TypeTree(),EmptyTree)
     val ecParam = ValDef(Modifiers(Flag.PARAM),ec,TypeTree(),EmptyTree)
     val nvaldefs = ecParam::ftParam::valdefs
     val nbody = q"""{
                      implicit val ${ft1} = ${ft}
                      implicit val ${ec1} = ${ec}
                      scala.async.Async.async(${transformDelayedMacroses(c)(body)})(${ec})
                     }
                  """
     val newTree = lastFun(nvaldefs,nbody)
     c.Expr[S](c.untypecheck(newTree))
   }

}

class ForeverSelectorBuilder(api: GopherAPI) extends SelectorBuilder[Unit](api)
{

         
   def reading[A](ch: Input[A])(f: A=>Unit): ForeverSelectorBuilder =
        macro SelectorBuilder.readingImpl[A,Unit,ForeverSelectorBuilder] 
                    // internal error in compiler when using this.type as S
      

   def readingWithFlowTerminationAsync[A](ch: Input[A], f: (ExecutionContext, FlowTermination[Unit], A) => Future[Unit] ): this.type =
   {
     val f1: ((A,ContRead[A,Unit]) => Option[Future[Continuated[Unit]]]) =
                            { (e, cr) => Some(f(ec,cr.flowTermination,e) map Function.const(cr)) }
     selector.addReader(ch,f1) 
     this
   }

   def writing[A](ch: Output[A], x: A)(body: Unit): ForeverSelectorBuilder = 
        macro SelectorBuilder.writingImpl[A,Unit,ForeverSelectorBuilder]

   def writingWithFlowTerminationAsync[A](ch:Output[A], x: =>A, f: (ExecutionContext, FlowTermination[Unit]) => Future[Unit] ): ForeverSelectorBuilder =
   {
     val f1: ContWrite[A,Unit] => Option[(A,Future[Continuated[Unit]])] =
                  { cw => Some(x,f(ec,cw.flowTermination) map Function.const(cw)) }
     selector.addWriter(ch,f1)
     this
   }


   def idle(body:Unit): ForeverSelectorBuilder =
         macro ForeverSelectorBuilder.idleImpl
    
   def idleWithFlowTerminationAsync(f: (ExecutionContext, FlowTermination[Unit]) => Future[Unit] ): ForeverSelectorBuilder =
   { val f1: (Skip[Unit] => Option[Future[Continuated[Unit]]]) =
                 { st => Some(f(ec,st.flowTermination) map Function.const(st)) }
     selector.addIdleSkip(f1)
     this
   }

    

}

object ForeverSelectorBuilder
{




   def idleImpl(c:Context)(body:c.Expr[Unit]):c.Expr[ForeverSelectorBuilder] =
   {
     import c.universe._
     SelectorBuilder.buildAsyncCall(c)(Nil,body.tree,
                   { (nvaldefs, nbody) =>
                      q"""${c.prefix}.idleWithFlowTerminationAsync(
                                    ${Function(nvaldefs,nbody)}
                          )
                       """
                   })
   }


}

class OnceSelectorBuilder[T](api: GopherAPI) extends SelectorBuilder[T@uncheckedVariance](api)
{

   def reading[A](ch: Input[A])(f: A=>T): OnceSelectorBuilder[T] =
        macro SelectorBuilder.readingImpl[A,T,OnceSelectorBuilder[T]] 

   def readingWithFlowTerminationAsync[A](ch: Input[A], f: (ExecutionContext, FlowTermination[T], A) => Future[T] ): OnceSelectorBuilder[T] =
   {
     val f1: ((A,ContRead[A,T]) => Option[Future[Continuated[T]]]) =
                            { (e, cr) => Some(f(ec,cr.flowTermination,e) map ( Done(_,cr.flowTermination))) }
     selector.addReader(ch,f1) 
     this
   }

   def writing[A](ch: Output[A], x: A)(body: T): OnceSelectorBuilder[T] = 
        macro SelectorBuilder.writingImpl[A,T,OnceSelectorBuilder[T]]
 
   def writingWithFlowTerminationAsync[A](ch:Output[A], x: =>A, f: (ExecutionContext, FlowTermination[T]) => Future[T] ): this.type =
   {
     val f1: ContWrite[A,T] => Option[(A,Future[Continuated[T]])] =
                  { cw => Some(x,f(ec,cw.flowTermination) map(x => Done(x,cw.flowTermination)) ) }
     selector.addWriter(ch,f1)
     this
   }

}


class SelectFactory(api: GopherAPI)
{
 
  /**
   * forever builder. 
   *@Seee ForeverSelectorBuilder
   */
  def forever: ForeverSelectorBuilder = new ForeverSelectorBuilder(api)

  def once[T]: OnceSelectorBuilder[T] = new OnceSelectorBuilder[T](api)

  /**
   * generic selector builder
   */
  def loop[A]: SelectorBuilder[A] = new SelectorBuilder[A](api)
}

