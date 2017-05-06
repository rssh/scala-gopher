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
    val nhof = transformHof[A,B](c)(hof.tree,List())
    val retval = c.Expr[Future[C]](q"${nhof}(${nf})")
    retval
  }

  def apply1i[A,B,C](hof:Function[Function[A,B],C])(nf:Function[A,Future[B]],implicitParams:Any*):Future[C] = macro AsyncApply.impl1i[A,B,C]


  def impl1i[A:c.WeakTypeTag,B:c.WeakTypeTag,C:c.WeakTypeTag](c:Context)(hof:c.Expr[Function[Function[A,B],C]])(nf:c.Expr[Function[A,Future[B]]],implicitParams:c.Expr[Any]*): c.Expr[Future[C]] =
  {
    import c.universe._
    val nhof = transformHof[A,B](c)(hof.tree,implicitParams.map(_.tree))
    val retval = (q"${nhof}(${nf})")
    c.Expr[Future[C]](retval)
  }

  def transformHof[A:c.WeakTypeTag,B:c.WeakTypeTag](c:Context)(hof:c.Tree,imps:Seq[c.Tree]):c.Tree = {
    import c.universe.{Function=>_,_}
    hof match {
      case q"${p}.$h" =>
               val ah = genAsyncName(c)(h,hof.pos)
               q"${p}.${ah}"
      case q"${p}.$h[..$w]" =>
               val ah = genAsyncName(c)(h,hof.pos)
               q"${p}.${ah}[..$w]"
      case q"($fp)=>$res($fp1)" if (fp.symbol == fp1.symbol) => 
               val nested = transformHof[A,B](c)(res,imps)
               val (paramName, paramDef) = createAsyncParam[A,B](c)(fp)
               val mfp2 = appendImplicitExecutionContext(c)(imps)
               val transformed = q"($paramDef)=>$nested($paramName)(..$mfp2)"
               c.typecheck(transformed)
      case q"($fp)=>$res($fp1)(..$fp2)" if (fp.symbol == fp1.symbol) => 
              // ..fp2 is a list of implicit params.
               val nested = transformHof[A,B](c)(res,imps)
               val (paramName, paramDef) = createAsyncParam[A,B](c)(fp)
               val mfp2 = appendImplicitExecutionContext(c)(fp2)
               val r = q"($paramDef)=>$nested($paramName)(..$mfp2)"
               r
      case q"($fp)=>$res[$w1,$w2]($fp1)($fp2)"  => 
               c.abort(hof.pos,"A1"+hof)
      case q"($fp:$ft)=>$a"  => 
               c.abort(hof.pos,"a="+a)
      case q"{ ..$stats }" =>
         try {
                val nstats = transformLast(c){
                               t => transformHof[A,B](c)(t,imps)
                             }(stats)
           q"{ ..$nstats }"
         } catch {
           case ex: Throwable =>
             System.err.println(s"error during transforming ${stats}")
             ex.printStackTrace()
             throw ex
         }
      case _ => c.abort(hof.pos,"hof match failed:"+hof+"\n raw:"+showRaw(hof))
    }
  }

  def createAsyncParam[A:c.WeakTypeTag,B:c.WeakTypeTag](c:Context)(fp:c.Tree):(c.TermName,c.Tree) =
  {
    //TODO: add ability to change future system.
    import c.universe._
    // TODO: check that fp is ident and get fp as name. 
    val nname = TermName(c.freshName())
    val paramType = tq"Function[${c.weakTypeOf[A]},scala.concurrent.Future[${c.weakTypeOf[B]}]]"
    (nname,ValDef(Modifiers(Flag.PARAM),nname,paramType,EmptyTree))
  }

  def inferImplicitExecutionContext(c:Context)():c.Tree =
  {
   val ect =  c.weakTypeOf[scala.concurrent.ExecutionContext]
   c.inferImplicitValue(ect, silent=false)
  }
  

  def appendImplicitExecutionContext(c:Context)(paramList:Seq[c.Tree]):Seq[c.Tree] =
  {
   val t = inferImplicitExecutionContext(c)()
   paramList.find(_.symbol == t.symbol) match {
     case None => paramList :+ t
     case Some(v) => paramList
   }
  }

/*
  def appendImplicitExecutionContextParam(c:Context)(paramList:List[c.Tree]):List[c.Tree]=
  {
    // check that paramList not contains ec.
    // (note, input must be typed
    paramList.find{ x =>
      x match {
        case ValDef(m,pn,pt,pv) =>
                m.hasFlag(Flag.IMPLICIT) && pt =:= c.weakTypeOf[scala.concurrent.ExecutionContext]
        case _ => false
      }
    } match {
      case None =>
         val pName = TermName(c.freshName("ec"))
         val pType = c.weakTypeOf[scala.concurrent.ExecutionContext]
         ValDef(Modifiers(Flag.PARAM|Flag.IMPLICIT),pName,paramType,EmptyTree)
      
    }
  }
*/

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
