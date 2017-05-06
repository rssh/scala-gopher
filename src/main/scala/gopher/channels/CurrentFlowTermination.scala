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
   def exit[A](a: A): A = ???

   def exitDelayed[A](a: A): A = 
          macro exitImpl[A]


   def doThrow(e: Throwable): Unit = 
          macro doThrowImpl


   def exitImpl[A](c:Context)(a: c.Expr[A])(implicit wtt: c.WeakTypeTag[A]): c.Expr[A]=
   {
    import c.universe._
    c.Expr[A](q"""
                    implicitly[_root_.gopher.FlowTermination[${wtt}]].doExit(${a})
                  """)
   }

   def doThrowImpl(c:Context)(e: c.Expr[Throwable]): c.Expr[Unit]=
   {
    import c.universe._
    c.Expr[Unit](q"implicitly[_root_.gopher.FlowTermination[Any]].doThrow(${e})")
   }

   def shutdownImpl(c:Context)(): c.Expr[Unit] =
   {
    import c.universe._
    exitImpl[Unit](c)(c.Expr[Unit](q"implicitly[_root_.gopher.FlowTermination[Unit]].doExit(())"))
   }


}
