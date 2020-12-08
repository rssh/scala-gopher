package gopher

import cps._

import scala.quoted._

class Select[F[_]:CpsSchedulingMonad](api: Gopher[F]):

    inline def apply[A](inline pf: PartialFunction[Any,A]): A =
      ${  Select.onceImpl[F,A]('pf)  }    

object Select:

  sealed trait SelectorCaseExpr
  case class ReadExpression[F[_],A, S](ch: Expr[ReadChannel[F,A]], f: Expr[(FlowTermination, A) => F[S]])
  case class WriteExpression[F[_],A, S](ch: Expr[WriteChannel[F,A]], a: Expr[A], f: Expr[FlowTermination => F[S]])
  case class DefaultExpression[F[_],S](ch: Expr[FlowTermination => F[S]])
  

  def onceImpl[F[_]:Type, A:Type](pf: Expr[PartialFunction[Any,A]])(using Quotes): Expr[A] =
    import quotes.reflect._
    onceImplTree[F,A](Term.of(pf)).asExprOf[A]

  def onceImplTree[F[_]:Type, A:Type](using Quotes)(pf: quotes.reflect.Term): quotes.reflect.Term =
    import quotes.reflect._
    pf match
      case Lambda(valDefs, body) =>
        onceImplTree[F,A](body)
      case Inlined(_,List(),body) => 
        onceImplTree[F,A](body)
      case Match(scrutinee,cases) =>
        val caseExprs = cases map(x => parseCaseDef(x))
    ???

  def parseCaseDef(using Quotes)(caseDef: quotes.reflect.CaseDef): SelectorCaseExpr =
    import quotes.reflect._
    caseDef.pattern match 
      case Inlined(_,List(),body) => 
            parseCaseDef(CaseDef(body, caseDef.guard, caseDef.rhs))
      case Typed(expr, TypeSelect(ch,"read")) =>
        if (caseDef.guard.isDefined) then
          report.error("guard in read should be empty", caseDef.asExpr)
        println(s"caseDef.rhs.tpe=${caseDef.rhs.tpe}" )
      case Bind(v, Typed(expr, TypeSelect(ch,"read"))) =>
          val paramName = v match
            case Ident(x) => x
            case _ =>
               report.error("expected identifier in read pattern", v.asExpr)
               "x"
          val mt = MethodType(List(paramName))(_ => List(v.tpe), _ => caseDef.rhs.tpe)
          val readFun = Lambda(Symbol.spliceOwner,mt,
                               (owner, args) => substIdent(body,v,args.head).changeOwner(owner))
          ReadExpression(ch.asExpr,readFun)
      case Bind(v, Typed(expr, TypeSelect(ch,"write"))) =>
          println(s"!!! write discovered" )
      case _ =>
        println(s"unparsed caseDef pattern: ${caseDef.pattern}" )
    ???

    
    def substIdent(using Quotes)(term: Term, fromTerm: Term, toTerm: Term):Term = ???

    
