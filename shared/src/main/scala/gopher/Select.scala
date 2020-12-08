package gopher

import cps._

import scala.quoted._

class Select[F[_]:CpsSchedulingMonad](api: Gopher[F]):

    inline def apply[A](inline pf: PartialFunction[Any,F[A]]): F[A] =
      ${  Select.onceImpl[F,A]('pf)  }    

object Select:

  sealed trait SelectorCaseExpr
  case class ReadExpression[F[_],A, S](ch: Expr[ReadChannel[F,A]], f: Expr[(FlowTermination[S], A) => F[S]])
  case class WriteExpression[F[_],A, S](ch: Expr[WriteChannel[F,A]], a: Expr[A], f: Expr[FlowTermination[S] => F[S]])
  case class DefaultExpression[F[_],S](ch: Expr[FlowTermination[S] => F[S]])
  

  def onceImpl[F[_]:Type, A:Type](pf: Expr[PartialFunction[Any,F[A]]])(using Quotes): Expr[F[A]] =
    import quotes.reflect._
    Term.of(pf) match
      case Match(scrutinee,cases) =>
        val caseExprs = cases map(x => parseCaseDef(x))
    ???

  def parseCaseDef(using Quotes)(caseDef: quotes.reflect.CaseDef): SelectorCaseExpr =
    import quotes.reflect._
    caseDef.pattern match 
      case Typed(expr, TypeSelect(ch,"Read")) =>
        if (caseDef.guard.isDefined) then
          report.error("guard in Read should be empty", caseDef.asExpr)
        println(s"caseDef.rhs.tpe=${caseDef.rhs.tpe}" )
    ???

    
  

    