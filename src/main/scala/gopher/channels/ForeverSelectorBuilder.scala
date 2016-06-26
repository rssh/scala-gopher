package gopher.channels

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import gopher._
import gopher.util._
import scala.concurrent._
import scala.concurrent.duration._
import scala.annotation.unchecked._


/**
 * Builder for 'forever' selector. Can be obtained as `gopherApi.select.forever`.
 **/
trait ForeverSelectorBuilder extends SelectorBuilder[Unit]
{

         
   def reading[A](ch: Input[A])(f: A=>Unit): ForeverSelectorBuilder =
        macro SelectorBuilder.readingImpl[A,Unit,ForeverSelectorBuilder] 
                    // internal error in compiler when using this.type as S
      

   def readingWithFlowTerminationAsync[A](ch: Input[A], f: (ExecutionContext, FlowTermination[Unit], A) => Future[Unit] ): this.type =
   {
     lazy val cont = ContRead(normalized, ch, selector)
     def normalized(_cont:ContRead[A,Unit]):Option[ContRead.In[A]=>Future[Continuated[Unit]]] = 
                                    Some(ContRead.liftIn(_cont)(a=>f(ec,selector,a) map Function.const(cont))) 
     withReader[A](ch, normalized) 
   }

   def writing[A](ch: Output[A], x: A)(f: A => Unit): ForeverSelectorBuilder = 
        macro SelectorBuilder.writingImpl[A,Unit,ForeverSelectorBuilder]

   @inline
   def writingWithFlowTerminationAsync[A](ch:Output[A], x: =>A, f: (ExecutionContext, FlowTermination[Unit], A) => Future[Unit] ): ForeverSelectorBuilder =
       withWriter[A](ch,   { cw => Some(x,f(ec,cw.flowTermination, x) map Function.const(cw)) } )

   def timeout(t:FiniteDuration)(f: FiniteDuration => Unit): ForeverSelectorBuilder =
        macro SelectorBuilder.timeoutImpl[Unit,ForeverSelectorBuilder]

 @inline
   def timeoutWithFlowTerminationAsync(t:FiniteDuration,
                       f: (ExecutionContext, FlowTermination[Unit], FiniteDuration) => Future[Unit] ): this.type =
        withTimeout(t){ sk => Some(f(ec,sk.flowTermination,t) map Function.const(sk)) }


   def idle(body:Unit): ForeverSelectorBuilder =
         macro SelectorBuilder.idleImpl[Unit,ForeverSelectorBuilder]
    

   /**
    * provide syntax for running select loop inside go (or async) block
    * example of usage:
    *
    *{{{
    *  go {
    *    .....
    *    for(s <- gopherApi.select.forever) 
    *      s match {
    *        case x: ch1.read => do something with x
    *        case q: chq.read => implicitly[FlowTermination[Unit]].doExit(())
    *        case y: ch2.write if (y=expr) => do something with y
    *        case _ => do somethig when idle.
    *      }
    *}}}
    *
    * Note, that you can use implicit instance of [FlowTermination[Unit]] to stop loop.
    **/
   def foreach(f:Any=>Unit):Unit = 
        macro SelectorBuilder.foreachImpl[Unit]

   

   /**
    * provide syntax for running select loop as async operation.
    *
    *{{{
    *  val receiver = gopherApi.select.forever{
    *                   case x: channel.read => Console.println(s"received:\$x")
    *                 }
    *}}}
    */
   def apply(f: PartialFunction[Any,Unit]): Future[Unit] =
        macro SelectorBuilder.applyImpl[Unit]


   def inputBuilder[B]() = new InputSelectorBuilder[B](api) 

   /**
    * provide syntax for creating output channels.
    *{{{
    *
    *  val multiplexed = for(s <- gopherApi.select.forever) yield 
    *       s match {
    *          case x: channelA => s"A:${x}"
    *          case x: channelB => s"B:${x}"
    *       }
    *
    *}}}
    **/
    def map[B](f:Any=>B):Input[B] = macro SelectorBuilder.mapImpl[B]
 
    def input[B](f:PartialFunction[Any,B]):Input[B] = 
                                    macro SelectorBuilder.inputImpl[B]

}



