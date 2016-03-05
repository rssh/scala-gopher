
import scala.language.experimental.macros
import scala.language.implicitConversions

/**
 * Provides scala API for 'go-like' CSP channels.
 * 
 *
 * == Overview ==
 *
 *  see <a href="https://github.com/rssh/scala-gopher/"> readme </a> for quick introduction.
 *
 * == Usage == 
 *  
 * At first you must receive gopherApi as Akka extension:
 *{{{
 *  import gopher._
 *
 *  .....
 *  val gopherApi = Gopher(actorSystem) 
 *}}}
 *
 * Then you can use CPS channels with blocling operations inside go clauses:
 *{{{
 *  val channel = gopherApi.makeChannel[Long]
 *  val n = 10000
 *  val producer = go {
 *   @volatile var(x,y) = (0L,1L)
 *   for( s <- gopherApi.select.forever) {
 *     case z: channel.write if (z==x) =>
 *                x = y
 *                y = x+z
 *                if (x > n) {
 *                   channel.close
 *                   implicitly[FlowTermination[Unit]].doExit()
 *                }
 *   }
 *  }
 *  val consumer = for((c,i) <- channel.zip(1 to n)) { 
 *     Console.println(s"fib(\${i})=\${c}")
 *  }
 *  Await.ready(consumer, 10 seconds)
 *}}}
 *
 * and defer/recover in go/goScope
 *
 *{{{
 * goScope{
 *   val f = openFile(myFileName)
 *   defer{
 *     if (! recover{case ex:FileNotFoundException => Console.println("invalid fname")}) {
 *        f.close()
 *     }
 *   }
 * }
 *}}}
 *
 *@see [[GopherAPI]]
 *@see [[channels.Channel]]
 *@see [[channels.Input]]
 *@see [[channels.Output]]
 *@see [[channels.SelectorBuilder]]
 *@see [[channels.SelectFactory]]
 *@author Ruslan Shevchenko <ruslan@shevchenko.kiev.ua>
 */
package object gopher {


import scala.concurrent._
import gopher.channels._
import gopher.goasync._

 //
 // magnetic arguments for selector-builder unsugared API
 //

 implicit def toAsyncFullReadSelectorArgument[A,B](
                   f: ContRead[A,B] => Option[ContRead.In[A] => Future[Continuated[B]]]
              ): ReadSelectorArgument[A,B] = AsyncFullReadSelectorArgument(f)  

 implicit def toAsyncNoOptionReadSelectorArgument[A,B](
                   f: ContRead[A,B] => (ContRead.In[A]=> Future[Continuated[B]])
               ): ReadSelectorArgument[A,B] = AsyncNoOptionReadSelectorArgument(f)

 implicit def toAsyncNoGenReadSelectorArgument[A,B](
                   f: ContRead[A,B] => (A => Future[Continuated[B]])
               ): ReadSelectorArgument[A,B] = AsyncNoGenReadSelectorArgument(f)

 implicit def toAsyncPairReadSelectorArgument[A,B](
                   f: (A, ContRead[A,B]) => Future[Continuated[B]]
               ): ReadSelectorArgument[A,B] = AsyncPairReadSelectorArgument(f)

 implicit def toSyncReadSelectorArgument[A,B](
                   f: ContRead[A,B] => (ContRead.In[A] => Continuated[B])
               ):ReadSelectorArgument[A,B] = SyncReadSelectorArgument(f)

 implicit def toSyncPairReadSelectorArgument[A,B](
                   f: (A, ContRead[A,B]) => Continuated[B]
               ):ReadSelectorArgument[A,B] = SyncPairReadSelectorArgument(f)



 implicit def toAsyncFullWriteSelectorArgument[A,B](
                   f: ContWrite[A,B] => Option[(A,Future[Continuated[B]])]
              ):WriteSelectorArgument[A,B] = AsyncFullWriteSelectorArgument(f)

 implicit def toAsyncNoOptWriteSelectorArgument[A,B](
                   f: ContWrite[A,B] => (A,Future[Continuated[B]])
              ):WriteSelectorArgument[A,B] = AsyncNoOptWriteSelectorArgument(f)

 implicit def toSyncWriteSelectorArgument[A,B](
                   f: ContWrite[A,B] => (A,Continuated[B])
              ): WriteSelectorArgument[A,B] = SyncWriteSelectorArgument(f)

 implicit def toAsyncFullSkipSelectorArgument[A](
                   f: Skip[A] => Option[Future[Continuated[A]]]
              ):SkipSelectorArgument[A] = AsyncFullSkipSelectorArgument(f)

 implicit def toAsyncNoOptSkipSelectorArgument[A](
                   f: Skip[A] => Future[Continuated[A]]
              ):SkipSelectorArgument[A] = AsyncNoOptSkipSelectorArgument(f)

 implicit def toSyncSelectorArgument[A](
                   f: Skip[A] => Continuated[A]
              ):SkipSelectorArgument[A] = SyncSelectorArgument(f)

//
// Time from time we forgott to set 'go' in selector builder. 
// Let's transform one automatically
//    TODO: make 'go' nilpotent before this. 
//
// implicit def toFuture[A](sb:SelectorBuilder[A]):Future[A] = sb.go

 @scala.annotation.compileTimeOnly("FlowTermination methods must be used inside flow scopes (go, reading/writing/idle args)")
 implicit def compileTimeFlowTermination[A]: FlowTermination[A] = ???

 /**
  * starts asyncronics execution of `body` in provided execution context. 
  * Inside go we can use `defer`/`recover` clauses and blocked read/write channel operations.  
  *
  */
 def go[T](body: T)(implicit ec:ExecutionContext) : Future[T] = macro GoAsync.goImpl[T]

 /**
  * provide access to using defer/recover inside body in the current thread of execution.
  */
 def goScope[T](body: T): T = macro GoAsync.goScopeImpl[T]

 /**
  * pseudostatement which can be used inside go/goScope block.
  **/
 @scala.annotation.compileTimeOnly("defer/recover method usage outside go / goScope ")
 def defer(x: =>Unit): Unit = ??? 

 /**
  * can be called only from defer block. If we in handling exception, try to apply <code> f </code>
  * to exception and if it's applied - stop panic and return true, otherwise return false.
  *
  *@param f - partial function for recovering exception.
  *@return true if exception was recovered, false otherwise
  */
 @scala.annotation.compileTimeOnly("defer/recover method usage outside go / goScope ")
 def recover[T](f: PartialFunction[Throwable, T]): Boolean = ??? 

 /**
  * sugar for reading value from future.
  */
 implicit class FutureWithRead[T](f:Future[T])
 {
   def read: T = macro InputMacro.read[T]

   def aread: Future[T] = f
 }

 //import scala.language.experimental.macros
 import scala.reflect.macros.blackbox.Context
 import scala.reflect.api._
 def awaitImpl[T](c:Context)(v:c.Expr[Future[T]]):c.Expr[T] =
 {
   import c.universe._
   c.Expr[T](q"scala.async.Async.await($v)")
 }

}

