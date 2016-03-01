package gopher.channels

import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context
import scala.reflect.api._
import gopher._
import gopher.util._
import scala.concurrent._
import scala.annotation.unchecked._


/**
 * Builder for 'once' selector. Can be obtained as `gopherApi.select.once`.
 */
trait OnceSelectorBuilder[T] extends SelectorBuilder[T@uncheckedVariance]
{

   def reading[A](ch: Input[A])(f: A=>T): OnceSelectorBuilder[T] =
        macro SelectorBuilder.readingImpl[A,T,OnceSelectorBuilder[T]] 

   @inline
   def readingWithFlowTerminationAsync[A](ch: Input[A], f: (ExecutionContext, FlowTermination[T], A) => Future[T] ): OnceSelectorBuilder[T] =
       withReader[A](ch,  { cr => Some(ContRead.liftIn(cr)(a => 
                                          f(ec,cr.flowTermination,a) map ( Done(_,cr.flowTermination)) 
                                      )                   ) 
                          } )

   /**
    * write x to channel if possible
    */
   def writing[A](ch: Output[A], x: A)(f: A=>T): OnceSelectorBuilder[T] = 
        macro SelectorBuilder.writingImpl[A,T,OnceSelectorBuilder[T]]
 
   @inline
   def writingWithFlowTerminationAsync[A](ch:Output[A], x: =>A, f: (ExecutionContext, FlowTermination[T], A) => Future[T] ): this.type =
        withWriter[A](ch, { cw => Some(x,f(ec,cw.flowTermination,x) map(x => Done(x,cw.flowTermination)) ) } )

   def idle(body: T): OnceSelectorBuilder[T] = 
        macro SelectorBuilder.idleImpl[T,OnceSelectorBuilder[T]]

   @inline
   def idleWithFlowTerminationAsync(f: (ExecutionContext, FlowTermination[T]) => Future[T] ): this.type =
       withIdle{ sk => Some(f(ec,sk.flowTermination) map(x => Done(x,sk.flowTermination)) ) }

   def foreach(f:Any=>T):T = 
        macro SelectorBuilder.foreachImpl[T]

   def apply(f: PartialFunction[Any,T]): Future[T] =
        macro SelectorBuilder.applyImpl[T]

}


