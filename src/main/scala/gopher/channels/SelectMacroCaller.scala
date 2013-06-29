package gopher.channels

import language.experimental.macros
import scala.concurrent.Future
import scala.reflect.macros.Context




object SelectorMacroCaller {

  def  foreach(x:SelectorContext => Unit):Unit = macro foreachImpl

  def  run(x:SelectorContext => Unit):Unit = macro foreachImpl

  def foreachImpl(c:Context)(x: c.Expr[SelectorContext=>Unit]):c.Expr[Unit] =
  {
   import c.universe._
   val xtree = x.tree
   System.err.println("foreaxh raw="+showRaw(x))
   val tree = Block(List(
                 ValDef(Modifiers(), newTermName("f"), TypeTree(), xtree)
                        ), 
                 Apply(Ident(newTermName("f")), 
                         List(Apply(
                               Select(New(
                                  Select(
                                    Select(
                                      Select(Ident(nme.ROOTPKG),
                                             newTermName("gopher")), 
                                      newTermName("channels")), 
                                    newTypeName("SelectorContext"))
                                  ), nme.CONSTRUCTOR), List()))))
   System.err.println("foreach output="+show(tree))
   c.Expr[Unit](c.resetAllAttrs(tree))
 }

//  def transformMatch

}

