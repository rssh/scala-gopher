package gopher.util

import scala.reflect.macros.blackbox.Context


trait ASTUtilImpl
{
  val c: Context
  import c.universe._

  def parseGuardInSelectorCaseDef(name: c.TermName, guard:c.Tree): c.Tree =
  {
     guard match {
        case Apply(Select(Ident(`name`),TermName("$eq$eq")),List(expression)) =>
               expression
        case _ =>
               c.abort(guard.pos, s"expected ${name}==<expression> in select guard")
     }
  }

}
