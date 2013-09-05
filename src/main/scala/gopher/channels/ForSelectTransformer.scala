package gopher.channels

import scala.language.experimental.macros
import scala.reflect.macros.Context
import scala.reflect.runtime.universe.TypeTag
import scala.concurrent._
import gopher._
import gopher.util._

//trait ForSelectContext[API] extends Tie[API]

class ForSelectTransformer[API <: ChannelsAPI[API]] {

  def  foreach(x: API#GTie => Unit): Unit = macro ForSelectTransformer.foreachImpl[API]
   
   object once {

     def  foreach(x: API#GTie => Unit): Unit = macro ForSelectTransformer.foreachImplOnce[API]  
  
    
   }


  
}


object ForSelectTransformer
{
  
   def  foreachImpl[API <: ChannelsAPI[API]](c:Context)(x: c.Expr[API#GTie => Unit]): c.Expr[Unit] =
     MacroHelper.implicitChannelApi[API](c).transformForSelect(c)(x)

     def foreachImplOnce[API <: ChannelsAPI[API]](c:Context)(x: c.Expr[ API#GTie => Unit]): c.Expr[Unit] =
             MacroHelper.implicitChannelApi[API](c).transformForSelectOnce(c)(x)
     

}