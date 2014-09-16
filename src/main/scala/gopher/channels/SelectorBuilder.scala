package gopher.channels

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import gopher._
import scala.concurrent._

sealed trait ReadSelectorArgument[A,B]
{
  def normalizedFun: (A, ContRead[A,B]) => Option[Future[Continuated[B]]]
}

case class AsyncFullReadSelectorArgument[A,B](
                   f: (A, ContRead[A,B]) => Option[Future[Continuated[B]]]
              )  extends ReadSelectorArgument[A,B]
{
  def normalizedFun = f
}

case class AsyncNoOptionReadSelectorArgument[A,B](
                   f: (A, ContRead[A,B]) => Future[Continuated[B]]
               ) extends ReadSelectorArgument[A,B]
{
  def normalizedFun = ( (a,c) => Some(f(a,c)) )
}

case class SyncReadSelectorArgument[A,B](
                   f: (A, ContRead[A,B]) => Continuated[B]
               ) extends ReadSelectorArgument[A,B]
{
  def normalizedFun = ( (a,c) => Some( Future successful f(a,c) ) )
}

sealed trait WriteSelectorArgument[A,B]
{
  def normalizedFun: ContWrite[A,B] => Option[(A,Future[Continuated[B]])]
}

case class AsyncFullWriteSelectorArgument[A,B](
                   f: ContWrite[A,B] => Option[(A,Future[Continuated[B]])]
              )  extends WriteSelectorArgument[A,B]
{
  def normalizedFun = f
}

case class AsyncNoOptWriteSelectorArgument[A,B](
                   f: ContWrite[A,B] => (A,Future[Continuated[B]])
              )  extends WriteSelectorArgument[A,B]
{
  def normalizedFun = (c => Some(f(c)))
}

case class SyncWriteSelectorArgument[A,B](
                   f: ContWrite[A,B] => (A,Continuated[B])
              )  extends WriteSelectorArgument[A,B]
{
  def normalizedFun = {c => 
     val (a, next) = f(c) 
     Some((a,Future successful next))
  }

}

sealed trait SkipSelectorArgument[A]
{
  def normalizedFun: Skip[A] => Option[Future[Continuated[A]]]
}

case class AsyncFullSkipSelectorArgument[A](
                   f: Skip[A] => Option[Future[Continuated[A]]]
              )  extends SkipSelectorArgument[A]
{
  def normalizedFun = f
}

case class AsyncNoOptSkipSelectorArgument[A](
                   f: Skip[A] => Future[Continuated[A]]
              )  extends SkipSelectorArgument[A]
{
  def normalizedFun = { c => Some(f(c)) }
}

case class SyncSelectorArgument[A](
                   f: Skip[A] => Continuated[A]
              )  extends SkipSelectorArgument[A]
{
  def normalizedFun = { c => Some(Future successful f(c)) }
}

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


class ForeverSelectorBuilder(api: GopherAPI) extends SelectorBuilder[Unit](api)
{

   thisSelectorBuilder =>

   def reading[A](ch: Input[A]) = new Reading[A](ch)

   class Reading[E](ch: Input[E]) 
   {
      def apply(f: E => Unit): ForeverSelectorBuilder =  
        macro ForeverSelectorBuilder.readingImpl[E]

      def withFlowTermination(f: (E, FlowTermination[Unit]) => Unit ): ForeverSelectorBuilder = 
        macro ForeverSelectorBuilder.readingWithFlowTerminationImpl[E]

      def withFlowTerminationAsync(f: (ExecutionContext, E,FlowTermination[Unit]) => Future[Unit] ): ForeverSelectorBuilder =
      {
        val f1: ((E,ContRead[E,Unit]) => Option[Future[Continuated[Unit]]]) =
                            { (e, cr) => Some(f(ec,e,cr.flowTermination) map Function.const(cr)) }
        selector.addReader(ch,f1) 
        thisSelectorBuilder
      }

   } 


   def writing[A](ch: Output[A], x: =>A) = new Writing[A](ch,x)

   class Writing[E](ch: Output[E], x: =>E) 
   {

      /**
       *@param - body: actions which needed 
       *  Note, that body is passed with 'byName' semantics, limitations of scala macros not allow to
       * show this in signature
       */
      def apply(body: Unit):ForeverSelectorBuilder =
           macro ForeverSelectorBuilder.writingImpl[E]

      def withFlowTerminationAsync(f: (ExecutionContext, FlowTermination[Unit]) => Future[Unit] ): ForeverSelectorBuilder =
      { val f1: ContWrite[E,Unit] => Option[(E,Future[Continuated[Unit]])] =
                  { cw => Some(x,f(ec,cw.flowTermination) map Function.const(cw)) }
        selector.addWriter(ch,f1)
        thisSelectorBuilder
      }


   }

   def idle = new Idle()

   class Idle
   {
       def apply(body: Unit): ForeverSelectorBuilder =
         macro ForeverSelectorBuilder.idleImpl

      def withFlowTerminationAsync(f: (ExecutionContext, FlowTermination[Unit]) => Future[Unit] ): ForeverSelectorBuilder =
      { val f1: (Skip[Unit] => Option[Future[Continuated[Unit]]]) =
                 { st => Some(f(ec,st.flowTermination) map Function.const(st)) }
        selector.addIdleSkip(f1)
        thisSelectorBuilder
      }

   }
    

}

object ForeverSelectorBuilder
{

   def readingImpl[A](c:Context)(f:c.Expr[A=>Unit]):c.Expr[ForeverSelectorBuilder] =
   {
      import c.universe._
      val newTree = f.tree match {
         case Function(valdefs, body) => 
               val elParam = valdefs match {
                               case el::Nil => el
                               case _ => c.abort(c.enclosingPosition,"Only one parameter to this function is expected")
                             }
               val ftParam = ValDef(Modifiers(Flag.PARAM|Flag.IMPLICIT),TermName("ft"),TypeTree(),EmptyTree)
               val ecParam = ValDef(Modifiers(Flag.PARAM|Flag.IMPLICIT),TermName("ec"),TypeTree(),EmptyTree)
               val nvaldefs = ecParam::elParam::ftParam::Nil
               val nbody = transformDelayedMacroses(c)(body)
               q"""${c.prefix}.withFlowTerminationAsync(
                               ${Function(nvaldefs,
                                          q"{scala.async.Async.async({${nbody};{}})(ec)}")}
                   )
                """
         case _ => c.abort(c.enclosingPosition,"argument of reading.apply must be function")
      }
      Console.println("newTree"+newTree)
      c.Expr[ForeverSelectorBuilder](c.untypecheck(newTree))
   }

   def readingWithFlowTerminationImpl[A](c:Context)(f:c.Expr[(A,FlowTermination[Unit])=>Unit]):
                                                                   c.Expr[ForeverSelectorBuilder] =
   {
      import c.universe._
      val newTree = f.tree match {
         case Function(valdefs, body) => 
               Console.println("matched, valdefs="+valdefs)
               // TODO: freshName instead ec.
               val nvaldefs = ValDef(Modifiers(Flag.PARAM|Flag.IMPLICIT),TermName("ec"),TypeTree(),EmptyTree)::valdefs
               q"""${c.prefix}.withFlowTerminationAsync(
                               ${Function(nvaldefs,q"scala.async.Async.async({${body}})(ec)")}
                            )
                        """
         case _ => c.abort(c.enclosingPosition,"argument of withFlowTermination must be function")
      }
      Console.println("newTree"+newTree)
      c.Expr[ForeverSelectorBuilder](c.untypecheck(newTree))
   }


   def transformDelayedMacroses(c:Context)(block:c.Tree):c.Tree =
   {
     import c.universe._

     val transformer = new Transformer {
        override def transform(tree:Tree): Tree =
          tree match {
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
                       System.err.println("apply, obj="+obj.tpe)
                       System.err.println("apply, member="+member)
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

   def writingImpl[A](c:Context)(body:c.Expr[Unit]):c.Expr[ForeverSelectorBuilder] =
   {
     import c.universe._
     val ftParam = ValDef(Modifiers(Flag.PARAM|Flag.IMPLICIT),TermName("ft"),TypeTree(),EmptyTree)
     val ecParam = ValDef(Modifiers(Flag.PARAM|Flag.IMPLICIT),TermName("ec"),TypeTree(),EmptyTree)
     val nvaldefs = ecParam::ftParam::Nil
     val nbody = transformDelayedMacroses(c)(body.tree)
     val newTree = q"""${c.prefix}.withFlowTerminationAsync(
                             ${Function(nvaldefs,q"scala.async.Async.async({${nbody}})(ec)")}
                       )
                   """
     c.Expr[ForeverSelectorBuilder](c.untypecheck(newTree))
   }


   def idleImpl(c:Context)(body:c.Expr[Unit]):c.Expr[ForeverSelectorBuilder] =
     writingImpl[Nothing](c)(body)

}

class OnceSelectorBuilder[+A](api: GopherAPI) extends SelectorBuilder[A@annotation.unchecked.uncheckedVariance](api)
{

   def onReadAsync[E, B >: A](ch:Input[E])(f: E => Future[B] ): 
                                           OnceSelectorBuilder[B] =
   {
     val f1: ((E,ContRead[E,B]) => Option[Future[Continuated[B]]]) =
           { (e, cr) => Some(f(e) map( Done(_,cr.flowTermination))) }
     selector.asInstanceOf[Selector[B]].addReader(ch,f1) 
     this.asInstanceOf[OnceSelectorBuilder[B]]
   }

}


class SelectFactory(api: GopherAPI)
{
 
  /**
   * forever builder. 
   *@Seee ForeverSelectorBuilder
   */
  def forever: ForeverSelectorBuilder = new ForeverSelectorBuilder(api)

  def once: OnceSelectorBuilder[Nothing] = new OnceSelectorBuilder(api)

  /**
   * generic selector builder
   */
  def loop[A]: SelectorBuilder[A] = new SelectorBuilder[A](api)
}

