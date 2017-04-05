package gopher.channels

import java.util.concurrent.atomic.AtomicBoolean

import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context
import scala.reflect.api._
import gopher._
import gopher.util._

import scala.concurrent._
import scala.concurrent.duration._
import scala.annotation.unchecked._


/**
 * Builder for 'input' selector. Can be obtained as `gopherApi.select.input`.
 * or map of forever selector.
 *
 * 
 */
class InputSelectorBuilder[T](override val api: GopherAPI) extends SelectorBuilder[T@uncheckedVariance]
                                                 with CloseableInput[T]
{

   val proxy = api.makeChannel[T]()
   val terminated = new AtomicBoolean(false)

   def reading[A](ch: Input[A])(f: A=>T): InputSelectorBuilder[T] =
        macro SelectorBuilder.readingImpl[A,T,InputSelectorBuilder[T]] 

   @inline
   def readingWithFlowTerminationAsync[A](ch: Input[A], f: (ExecutionContext, FlowTermination[T], A) => Future[T] ): InputSelectorBuilder[T] =
   {
      def normalized(_cont:ContRead[A,T]):Option[ContRead.In[A]=>Future[Continuated[T]]] =
              Some(ContRead.liftIn(_cont)(a=>f(ec,selector,a) flatMap { x =>
                    if (!terminated.get) {
                      proxy.awrite(x)
                    } else Future successful x
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

/*
   def idle(body: T): InputSelectorBuilder[T] = 
        macro SelectorBuilder.idleImpl[T,InputSelectorBuilder[T]]

   @inline
   def idleWithFlowTerminationAsync(f: (ExecutionContext, FlowTermination[T]) => Future[T] ): this.type =
       withIdle{ sk => Some(f(ec,sk.flowTermination) flatMap(x => 
                            proxy.awrite(x)) map(Function.const(sk)) ) }
*/

   def timeout(t:FiniteDuration)(f: FiniteDuration => T): InputSelectorBuilder[T] =
        macro SelectorBuilder.timeoutImpl[T,InputSelectorBuilder[T]]

   @inline
   def timeoutWithFlowTerminationAsync(t:FiniteDuration, 
                       f: (ExecutionContext, FlowTermination[T], FiniteDuration) => Future[T] ): this.type =
        withTimeout(t){ sk => Some(f(ec,sk.flowTermination,t) flatMap( x =>
                              proxy.awrite(x)) map(Function.const(sk)) ) }

  def handleError(f: Throwable => T): InputSelectorBuilder[T] =
       macro SelectorBuilder.handleErrorImpl[T,InputSelectorBuilder[T]]

  @inline
  def handleErrorWithFlowTerminationAsync(f: (ExecutionContext, FlowTermination[T], Continuated[T], Throwable) => Future[T] ): this.type =
    withError{  (ec,ft,cont,ex) =>
      f(ec,ft,cont,ex).flatMap(x=> proxy.awrite(x).map(Function.const(cont))(ec))(ec)
    }


   def foreach(f:Any=>T):T = 
        macro SelectorBuilderImpl.foreach[T]

   def apply(f: PartialFunction[Any,T]): Future[T] =
        macro SelectorBuilderImpl.apply[T]

   // input methods
   def  cbread[B](f:
            ContRead[T,B]=>Option[
                    ContRead.In[T]=>Future[Continuated[B]]
            ],
            ft: FlowTermination[B]): Unit = proxy.cbread(f,ft)

   val  done: Input[Unit] = proxy.done

   def started: InputSelectorBuilder[T] = { go; this }
  
   // 
   override val selector = new Selector[T](api) {
          override def doExit(a: T): T =
          {
            if (terminated.compareAndSet(false,true)) {
              proxy.awrite(a) onComplete {
                _ => proxy.close()
              }
              //proxy.close()
            }
            super.doExit(a)
          }

   }

}


