package gopher.channels

import scala.concurrent._
import scala.concurrent.duration._
import scala.async.Async._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import gopher._

/**
 * Entity, where we can write objects of type A.
 *
 */
trait Output[A]
{


  def  awriteu(a:A):Future[Unit] = ???

  def write(a:A):Unit = macro Output.writeImpl[A]


}

object Output
{

  def writeImpl[A](c:Context)(a:c.Expr[A]):c.Expr[Unit] =
  {
   import c.universe._
   c.Expr[Unit](q"scala.async.Async.await(${c.prefix}.awriteu(${a}))")
  }


}
