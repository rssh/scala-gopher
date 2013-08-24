package gopher.channels

import scala.language.experimental.macros
import scala.reflect.macros.Context
import scala.concurrent._
import gopher._
import gopher.util._

trait ForSelectContext extends Tie

object ForSelectTransformer {

  def  foreach(x: Tie => Unit): Unit = macro foreachImpl
   
   def  foreachImpl(c:Context)(x: c.Expr[Tie => Unit]): c.Expr[Unit] =
     MacroHelper.implicitChannelApi(c).transformForSelect(c)(x)
   
   object once {
    
     def  foreach(x: Tie => Unit): Unit = macro foreachImpl  
    
     def foreachImpl(c:Context)(x: c.Expr[Tie => Unit]): c.Expr[Unit] =
             MacroHelper.implicitChannelApi(c).transformForSelectOnce(c)(x)
    
   }


  
}