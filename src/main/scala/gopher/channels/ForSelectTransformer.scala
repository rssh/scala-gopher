package gopher.channels

import scala.language.experimental.macros
import scala.reflect.macros.Context
import scala.concurrent._

trait ForSelectContext extends Tie

object ForSelectTransformer {

  def  foreach(x: Tie => Unit): Unit = macro foreachImpl
   
   def  foreachImpl(c:Context)(x: c.Expr[Tie => Unit]): c.Expr[Unit] =
     implicitChannelApi(c).transformForSelect(c)(x)
   
   object once {
    
     def  foreach(x: Tie => Unit): Unit = macro foreachImpl  
    
     def foreachImpl(c:Context)(x: c.Expr[Tie => Unit]): c.Expr[Unit] =
             implicitChannelApi(c).transformForSelectOnce(c)(x)
    
   }

   
   private def implicitChannelApi(c:Context): ChannelsAPI =
   {
     import c.universe._
     val channelsApiTree = c.inferImplicitValue(typeOf[ChannelsAPI])
     // resetAllAttrs, becouse eval not like type tree.
     c.eval(c.Expr[ChannelsAPI](c.resetAllAttrs(channelsApiTree)))
   }
   
  
}