package gopher.channels

import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context
import scala.reflect.api._
import gopher._
import gopher.util._
import scala.concurrent._
import scala.annotation.unchecked._


/**
 * Builder for 'input' selector. Can be obtained as `gopherApi.select.input`.
 * or map of forever selector.
 *
 * 
 */
class InputSelectorBuilder[T](override val api: GopherAPI) extends SelectorBuilder[T@uncheckedVariance]
                                                 with Input[T]
{

   val proxy = api.makeChannel[T]()

   def reading[A](ch: Input[A])(f: A=>T): InputSelectorBuilder[T] =
        macro SelectorBuilder.readingImpl[A,T,InputSelectorBuilder[T]] 

   @inline
   def readingWithFlowTerminationAsync[A](ch: Input[A], f: (ExecutionContext, FlowTermination[T], A) => Future[T] ): InputSelectorBuilder[T] =
   {
      def normalized(_cont:ContRead[A,T]):Option[ContRead.In[A]=>Future[Continuated[T]]] =
              Some(ContRead.liftIn(_cont)(a=>f(ec,selector,a) flatMap {
                    proxy.awrite(_)
                  } map Function.const(ContRead(normalized,ch,selector))))
      withReader[A](ch,normalized)
   }

   /**
    * write x to channel if possible
    */
   def writing[A](ch: Output[A], x: A)(f: A=>T): InputSelectorBuilder[T] = 
        macro SelectorBuilder.writingImpl[A,T,InputSelectorBuilder[T]]
 
   @inline
   def writingWithFlowTerminationAsync[A](ch:Output[A], x: =>A, f: (ExecutionContext, FlowTermination[T], A) => Future[T] ): this.type =
        withWriter[A](ch, { cw => Some(x,f(ec,cw.flowTermination,x) flatMap {
                                  x=>proxy.awrite(x)
                          } map Function.const(cw)) })

   def idle(body: T): InputSelectorBuilder[T] = 
        macro SelectorBuilder.idleImpl[T,InputSelectorBuilder[T]]

   @inline
   def idleWithFlowTerminationAsync(f: (ExecutionContext, FlowTermination[T]) => Future[T] ): this.type =
       withIdle{ sk => Some(f(ec,sk.flowTermination) flatMap(x => 
                            proxy.awrite(x)) map(Function.const(sk)) ) }

   def foreach(f:Any=>T):T = 
        macro SelectorBuilder.foreachImpl[T]

   def apply(f: PartialFunction[Any,T]): Future[T] =
        macro SelectorBuilder.applyImpl[T]

   // input methods
   def  cbread[B](f:
            ContRead[T,B]=>Option[
                    ContRead.In[T]=>Future[Continuated[B]]
            ],
            ft: FlowTermination[B]): Unit = proxy.cbread(f,ft)
  
   def started: InputSelectorBuilder[T] = { go; this }
  
   // 
   override val selector = new Selector[T](api) {
          override def doExit(a: T): T =
          {
            proxy.awrite(a) onComplete {
              _ => proxy.close()
            }
            super.doExit(a)
          }
   }

}


