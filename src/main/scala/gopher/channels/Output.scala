package gopher.channels

import scala.concurrent._
import scala.async.Async._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._

/**
 * Entity, which can 'eat' objects of type A,
 * can be part of channel
 */
trait Output[A]
{

  /**
   * apply f and send result to channels processor.
   */
  def  cbwrite[B](f: ContWrite[A,B] => Option[(A,Future[Continuated[B]])], ft: FlowTermination[B]): Unit

  def  awrite(a:A):Future[Unit] =
  {
   val ft = PromiseFlowTermination[Unit]()
   cbwrite[Unit]( cont => {
            Some((a,Future.successful(Done((),ft))))
          }, 
          ft
         )
   ft.future
  }
  
  /**
   * 'blocked' write of 'a' to channel.
   * Note, that this method can be called only inside
   * 'go' or 'async' blocks, since blocking is
   * emulated by 'Async.await'
   **/
  def write(a:A):Unit = macro OutputMacro.write[A]


  def awriteAll[C <: Iterable[A]](c:C):Future[Unit] =
  {
    if (c.isEmpty) {
      Future successful (())
    } else {
      val ft = PromiseFlowTermination[Unit]
      val it = c.iterator
      def f(cont:ContWrite[A,Unit]):Option[(A,Future[Continuated[Unit]])]=
      {
          val n = it.next()
          if (it.hasNext) {
            Some((n,Future successful cont))
          } else {
            Some((n, Future successful Done((), ft) ))
          }
      }         
      cbwrite(f,ft)
      ft.future
    }
  }

}

object OutputMacro
{

  def write[A](c:Context)(a:c.Expr[A]):c.Expr[Unit] =
  {
   import c.universe._
   c.Expr[Unit](q"scala.async.Async.await(${c.prefix}.awrite(${a}))")
  }

}
