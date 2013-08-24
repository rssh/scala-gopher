package gopher.util

import language.experimental.macros
import scala.reflect.macros.Context
import gopher.channels._

/**
 * Helper for common macro operations.
 */
object MacroHelper {

   /**
   * resolve and instantiate implicit channels Api in macro context.
   * (used for running macros-part of channels API during compilation)
   */
  def implicitChannelApi(c:Context): ChannelsAPI =
  {
     import c.universe._
     val channelsApiTree = c.inferImplicitValue(typeOf[ChannelsAPI])
     // resetAllAttrs, becouse eval not like type tree.
     c.eval(c.Expr[ChannelsAPI](c.resetAllAttrs(channelsApiTree)))
  }
 
  
  
}