package gopher.channels

import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context
import scala.reflect.api._
import gopher._
import scala.concurrent._
import scala.annotation._

object CurrentFlowTermination 
{

   @compileTimeOnly("defer must be used only inside goScope or selector callbacks")
   def defer(body: Unit)(implicit ec: ExecutionContext): Unit = ???

   // note, that this call-by-name, it's not writeln in macroses for some reason.
   def deferDelayed(body: Unit)(implicit ec: ExecutionContext): Unit =  macro deferImpl

   @compileTimeOnly("exit must be used only inside goScope or selector callbacks")
   def exit[A](a: A): Unit = ???

   def exitDelayed[A](a: A): Unit = 
          macro exitImpl[A]


   def doThrow(e: Throwable): Unit = 
          macro doThrowImpl

   def deferImpl(c:Context)(body: c.Expr[Unit])(ec: c.Expr[ExecutionContext]): c.Expr[Unit]=
   {
    import c.universe._
    c.Expr[Unit](q"implicitly[FlowTermination[_]].defer(${body})")
   }

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
