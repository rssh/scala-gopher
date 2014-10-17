package gopher.util

import scala.reflect.macros.blackbox.Context
import scala.reflect.api._


object MacroUtil
{

  /**
   * short representation of tree, suitable for show in 
   * error messages.
   */
  def  shortString(c:Context)(x:c.Tree):String =
  {
   val raw = c.universe.showRaw(x)
   if (raw.length > SHORT_LEN) {
       raw.substring(0,raw.length-3)+"..."
   } else {
       raw
   }
  }

  final val SHORT_LEN = 80
}
