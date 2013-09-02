package gopher.util

import language.experimental.macros
import scala.reflect.macros.Context
import scala.reflect.runtime.universe.TypeTag
import gopher.channels._

/**
 * Helper for common macro operations.
 */
object MacroHelper {

   /**
   * resolve and instantiate implicit channels Api in macro context.
   * (used for running macros-part of channels API during compilation)
   */
  def implicitChannelApi[API <: ChannelsAPI[API]](c:Context): ChannelsAPI[API] =
  {
     import c.universe._
     val channelsApiTree = c.inferImplicitValue(typeOf[ChannelsAPI[_]])
     // resetAllAttrs, becouse eval not like type tree.
     c.eval(c.Expr[ChannelsAPI[API]](c.resetAllAttrs(channelsApiTree)))
  }
 
  
  
}