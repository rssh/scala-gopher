package gopher.channels

import scala.concurrent._
import scala.concurrent.duration._
import scala.async.Async._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import gopher._

trait Output[A]
{

  type ~> = A
  type writeExp[X] = A
  type write = A


  /**
   * apply f and send result to channels processor.
   */
  def  cbwrite[B](f: ContWrite[A,B] => Option[
                   (A,Future[Continuated[B]])
                  ], 
                  ft: FlowTermination[B]): Unit

  def api: GopherAPI 

  def  awrite(a:A):Future[A] = ???
  
  /**
   * 'blocking' write of 'a' to channel.
   * Note, that this method can be called only inside
   * 'go' or 'async' blocks.
   **/
  def write(a:A):A = macro Output.writeImpl[A]

  /**
   * shortcut for blocking write.
   */
  def <~ (a:A):Output[A] = macro Output.writeWithBuilderImpl[A] 

  /**
   * shortcut for blocking write.
   */
  def !(a:A):Unit = macro Output.writeImpl[A]


  def awriteAll[C <: Iterable[A]](c:C):Future[Unit] = ???

  def writeAll[C <: Iterable[A]](it:C):Unit = macro Output.writeAllImpl[A,C]

  

}

object Output
{

  def writeImpl[A](c:Context)(a:c.Expr[A]):c.Expr[A] =
  {
   import c.universe._
   c.Expr[A](q"scala.async.Async.await(${c.prefix}.awrite(${a}))")
  }

  def writeAllImpl[A,C](c:Context)(it:c.Expr[C]):c.Expr[Unit] =
  {
   import c.universe._
   c.Expr[Unit](q"scala.async.Async.await(${c.prefix}.writeAll(${it}))")
  }


  def writeWithBuilderImpl[A](c:Context)(a:c.Expr[A]):c.Expr[Output[A]] =
  {
   import c.universe._
   val retval = c.Expr[Output[A]](
     q"""{
          val prefix = ${c.prefix}
          scala.async.Async.await{prefix.awrite(${a});{}}
          prefix 
         }
      """
   )
   retval
  }


}
