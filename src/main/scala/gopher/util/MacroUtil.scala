package gopher.util

import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import scala.language.reflectiveCalls


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

  def skipAnnotation(c:Context)(x: c.Tree):c.Tree =
  {
     import c.universe._
     x match {
        case Annotated(_,arg) => arg
        case _ => x
     }
  }

  def hasAwait(c:Context)(x: c.Tree):Boolean =
  {
    import c.universe._
    val findAwait = new Traverser {
      var found = false
      override def traverse(tree:Tree):Unit =
      {
       if (!found) {
         tree match {
            case Apply(TypeApply(Select(obj,TermName("await")),objType), args) =>
                   if (obj.tpe =:= typeOf[scala.async.Async.type]) {
                       found=true
                   } else super.traverse(tree)
            case _ => super.traverse(tree)
         }
       }
      }
   }
   findAwait.traverse(x)
   findAwait.found
  }



  final val SHORT_LEN = 80
}
