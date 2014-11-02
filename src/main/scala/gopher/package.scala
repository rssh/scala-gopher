
import scala.language.experimental.macros

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
 *@see [[channels.IOChannel]]
 *@see [[channels.SelectorBuilder]]
 *@see [[channels.SelectFactory]]
 *@author Ruslan Shevchenko <ruslan@shevchenko.kiev.ua>
 */
package object gopher {


import scala.concurrent._
import gopher.channels._
import gopher.goasync._


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


}

