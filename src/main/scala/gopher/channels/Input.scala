package gopher.channels

import scala.concurrent._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import gopher._


/**
 * Entity, which can read (or generate, as you prefer) objects of type A,
 * can be part of channel
 */
trait Input[A]
{

  type <~ = A
  type read = A

  /**
   * apply f, when input will be ready and send result to API processor
   */
  def  cbread[B](f: (A, ContRead[A,B]) => Option[Future[Continuated[B]]], flwt: FlowTermination[B] ): Unit

  def  aread:Future[A] = {
    val ft = PromiseFlowTermination[A]() 
    cbread[A]( (a, self) => { Some(Future.successful(Done(a,ft))) }, ft )
    ft.future
  }

  def  read:A = macro InputMacro.read[A]

  def atake(n:Int):Future[IndexedSeq[A]] =
  {
    if (n==0) {
      Future successful IndexedSeq()
    } else {
       val ft = PromiseFlowTermination[IndexedSeq[A]]
       var i = 1;
       var r: IndexedSeq[A] = IndexedSeq()
       cbread({
        (a:A, c:ContRead[A,IndexedSeq[A]]) => 
          i=i+1
          r = r :+ a
          if (i<n) {
             Some(Future successful c)
          } else {
             Some(Future successful Done(r,ft))
          }
        },ft)
        ft.future
    }
  }


}

object InputMacro
{

  def read[A](c:Context):c.Expr[A] =
  {
   import c.universe._
   c.Expr[A](q"{scala.async.Async.await(${c.prefix}.aread)}")
  }

}
