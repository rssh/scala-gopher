package gopher.goasync

import scala.language.experimental.macros
import scala.language.reflectiveCalls
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import gopher._
import gopher.util._
import scala.concurrent._
import scala.annotation.unchecked._



object AsyncApply
{


  def impl1[A:c.WeakTypeTag,B:c.WeakTypeTag,C:c.WeakTypeTag](c:Context)(hof:c.Expr[Function[Function[A,B],C]])(nf:c.Expr[Function[A,Future[B]]]):
                                                                                    c.Expr[Future[C]] =
  {
    import c.universe._
    val nhof = transformHof[A,B,C](c)(hof.tree)
    val retval = c.Expr[Future[C]](q"${nhof}(${nf})")
    System.err.println("retval:"+retval)
    retval
  }

  def transformHof[A:c.WeakTypeTag,B:c.WeakTypeTag,C:c.WeakTypeTag](c:Context)(hof:c.Tree):c.Tree = {
    import c.universe.{Function=>_,_}
    hof match {
      case q"${p}.$h" =>
               val ah = genAsyncName(c)(h,hof.pos)
               q"${p}.${ah}"
      case q"${p}.$h[$w]" =>
               val ah = genAsyncName(c)(h,hof.pos)
               q"${p}.${ah}[$w]"
      case q"($fp)=>$res($fp1)" if (fp.symbol == fp1.symbol) => 
               val nested = transformHof[A,B,C](c)(res)
               val nname = TermName(c.freshName())
               val paramType = tq"Function[${c.weakTypeOf[A]},Future[${c.weakTypeOf[B]}]]"
               //val paramDef = q"val $nname:${paramType}"
               val paramDef = ValDef(Modifiers(Flag.PARAM),nname,paramType,EmptyTree)
               c.typecheck(q"($paramDef)=>$nested($nname)")
      case q"{ ..$stats }" =>
                val nstats = transformLast(c){
                               t => transformHof[A,B,C](c)(t)
                             }(stats)
           q"{ ..$nstats }"
      case _ => c.abort(hof.pos,"hof match failed:"+hof)
    }
  }

  def genAsyncName(c:Context)(h:c.TermName,pos:c.Position):c.TermName =
  {
    import c.universe._
    h match {
       case TermName(hname) => 
              TermName(hname+"Async")
       case _ =>
              c.abort(pos,"ident expected for hight order function")
    }
  }

  def transformLast(c:Context)(f:c.Tree=>c.Tree)(block: List[c.Tree]):List[c.Tree] =
   block match {
     case Nil => Nil
     case r::Nil => f(r)::Nil
     case h::q => h::transformLast(c)(f)(q)
   }

}
