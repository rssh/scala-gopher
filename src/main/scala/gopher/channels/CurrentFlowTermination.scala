package gopher.channels

import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context
import scala.reflect.api._
import gopher._
import scala.concurrent._
import scala.annotation._

object CurrentFlowTermination
{


   @compileTimeOnly("exit must be used only inside goScope or selector callbacks")
   def exit[A](a: A): Unit = ???

   def exitDelayed[A](a: A): Unit = 
          macro exitImpl[A]


   def doThrow(e: Throwable): Unit = 
          macro doThrowImpl


   def exitImpl[A:c.WeakTypeTag](c:Context)(a: c.Expr[A])(implicit wtt: c.WeakTypeTag[A]): c.Expr[Unit]=
   {
    import c.universe._
    c.Expr[Unit](q"implicitly[FlowTermination[${wtt}]].doExit(${a})")
   }

   def doThrowImpl(c:Context)(e: c.Expr[Throwable]): c.Expr[Unit]=
   {
    import c.universe._
    c.Expr[Unit](q"implicitly[FlowTermination[Any]].doThrow(${e})")
   }


}
