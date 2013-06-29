package gopher.scope

import language.experimental.macros
import scala.reflect.macros.Context
import scala.reflect.api._



object Macroses
{

  def go[A](x: A) = macro transformImpl[A]

  def defer[A](x: =>A) = 
   {
    x
    //throw new IllegalStateExcexption("go must never called directly");
   }

  def transform[A](x: A) = macro transformImpl[A]

  def transformImpl[A](c:Context)(x: c.Expr[A]): c.Expr[A] =
  {
    import c.universe._
    if (findDeffered(c)(x.tree)) {
       withDeffered(c)(x)
    } else {
       x
    }
  }

  def withDeffered[A](c:Context)(x: c.Expr[A]): c.Expr[A] =
  {
    import c.universe._
    val tree = Block(
                List(
                  Apply(Select(Select(Ident(newTermName("System")), newTermName("err")), newTermName("println")), List(Literal(Constant("enter"))))
                ),
                Try(
                  x.tree,
                  Nil,
                  Apply(Select(Select(Ident(newTermName("System")), newTermName("err")), newTermName("println")), List(Literal(Constant("leave"))))
                )
              )
    c.Expr[A](tree)
  } 


  def findDefferedInList(c:Context)(x: List[c.Tree]): Boolean = 
    x.find(findDeffered(c)).isDefined

  def findDeffered(c:Context)(x: c.Tree): Boolean = 
  {
    @inline def find(t: c.Tree) = findDeffered(c)(t)
    @inline def findl(l: List[c.Tree]) = findDefferedInList(c)(l)
    import c.universe._
    val DEFER = newTermName("defer")
    x match {
      case ClassDef(_,_,_,_) => false
      case ModuleDef(_,_,_) => false
      case ValDef(_,_,tpt,rhs) => find(rhs)
      case x: DefDef => false
      case x: TypeDef => false
      case LabelDef(_,_,rhs) => find(rhs)
      case Block(stats, expr) => findl(stats) || find(expr)
      case Match(selector, cases) =>  find(selector) || findl(cases)
      case CaseDef(pat, guard, body) => find(body)
      case Alternative(trees) => false // impossible
      case Function(vparams, body) => find(body)
      case Assign(lhs, rhs) => find(lhs) || find(rhs)
      case AssignOrNamedArg(lhs, rhs) =>  find(rhs)
      case If(cond, thenp, elsep) =>  find(cond) || find(thenp) || find(elsep)
      case Return(expr) => find(expr)
      case Try(block, catches, finalizer) => find(block) || findl(catches) || find(finalizer)
      case Typed(expr, tpt) => find(expr)
      case Apply(fun, args) =>
            fun match {
              case Ident(x) => 
                     System.err.println("apply, x="+x);
                     if (x==DEFER) {
                        System.err.println("== works")
                        true
                     } else false
              case _ => find(fun) || findl(args)
            }
      case Select(qualifier, name) => find(qualifier)
      case Annotated(annot, arg) => find(arg)
      case _ => false

    }
  }


}
