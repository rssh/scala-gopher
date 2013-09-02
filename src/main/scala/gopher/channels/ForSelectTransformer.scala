package gopher.channels

import scala.language.experimental.macros
import scala.reflect.macros.Context
import scala.reflect.runtime.universe.TypeTag
import scala.concurrent._
import gopher._
import gopher.util._

//trait ForSelectContext[API] extends Tie[API]

object ForSelectTransformer {

  def  foreach[API <: ChannelsAPI[API]](x: API#GTie => Unit): Unit = macro foreachImpl[API]
   
   def  foreachImpl[API <: ChannelsAPI[API]](c:Context)(x: c.Expr[API#GTie => Unit]): c.Expr[Unit] =
     MacroHelper.implicitChannelApi[API](c).transformForSelect(c)(x)
   
   object once {
    
     def  foreach[API <: ChannelsAPI[API] ](x: API#GTie => Unit): Unit = macro foreachImpl[API]  
    
     def foreachImpl[API <: ChannelsAPI[API]](c:Context)(x: c.Expr[ API#GTie => Unit]): c.Expr[Unit] =
             MacroHelper.implicitChannelApi[API](c).transformForSelectOnce(c)(x)
    
   }


  
}