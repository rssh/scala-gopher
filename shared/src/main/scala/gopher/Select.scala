package gopher

import cps._

import scala.quoted._

class Select[F[_]:CpsSchedulingMonad](api: Gopher[F]):

    inline def apply[A](inline pf: PartialFunction[Any,A]): A =
      ${  Select.onceImpl[F,A]('pf)  }    

object Select:

  sealed trait SelectorCaseExpr
  case class ReadExpression[F[_], A, S](ch: Expr[ReadChannel[F,A]], f: Expr[A => S]) extends SelectorCaseExpr
  case class WriteExpression[F[_], A, S](ch: Expr[WriteChannel[F,A]], a: Expr[A], f: Expr[() => F[S]]) extends SelectorCaseExpr
  case class DefaultExpression[S](ch: Expr[ () => S ]) extends SelectorCaseExpr
  

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
        val caseExprs = cases map(x => parseCaseDef[F,A](x))
    ???

  def parseCaseDef[F[_]:Type,S:Type](using Quotes)(caseDef: quotes.reflect.CaseDef): SelectorCaseExpr =
    import quotes.reflect._
    caseDef.pattern match 
      case Inlined(_,List(),body) => 
            parseCaseDef(CaseDef(body, caseDef.guard, caseDef.rhs))
      case Typed(expr, tp@TypeSelect(ch,"read")) =>
        if (caseDef.guard.isDefined) then
          report.error("guard in read should be empty", caseDef.asExpr)
        println(s"caseDef.rhs.tpe=${caseDef.rhs.tpe}" )
      case b@Bind(v, tp@Typed(expr, TypeSelect(ch,"read"))) =>
          val mt = MethodType(List(v))(_ => List(tp.tpe), _ => caseDef.rhs.tpe)
          val readFun = Lambda(Symbol.spliceOwner,mt,
                               (owner, args) => substIdent(caseDef.rhs,b.symbol,args.head.asInstanceOf[Term], owner).changeOwner(owner))
          if (ch.tpe <:< TypeRepr.of[ReadChannel[F,?]]) 
            tp.tpe.asType match
              case '[a] => ReadExpression(ch.asExprOf[ReadChannel[F,a]],readFun.asExprOf[a=>S])
              case _ => 
                report.error("can't determinate read type", caseDef.pattern.asExpr)
                throw new RuntimeException("Can't determinae read type")
          else
            report.error("read pattern is not a read channel", ch.asExpr)
            throw new RuntimeException("Incorrect select caseDef")
      case Bind(v, tp@Typed(expr, TypeSelect(ch,"write"))) =>
          val mt = MethodType(List())(_ => List(), _ => caseDef.rhs.tpe)
          val writeFun = Lambda(Symbol.spliceOwner,mt, (owner,args) => caseDef.rhs.changeOwner(owner))    
          val e = caseDef.guard match
            case Some(condition) =>
              condition match
                case Apply(quotes.reflect.Select(Ident(v1),method),List(expr)) =>
                  if (v1 != v) {
                     report.error(s"write name mismatch ${v1}, expected ${v}", condition.asExpr)
                     throw new RuntimeException("macro failed")
                  }
                  expr
                case _ =>  
                  report.error(s"Condition is not in form x==expr,${condition} ",condition.asExpr)
                  throw new RuntimeException("condition is not in writing form")
          if (ch.tpe <:< TypeRepr.of[WriteChannel[F,?]]) then
            tp.tpe.asType match
              case '[a] => 
                WriteExpression(ch.asExprOf[WriteChannel[F,a]],e.asExprOf[a], writeFun.asExprOf[Unit=>S]) 
              case _ =>
                report.error("Can't determinate ") 
      case _ =>
        println(s"unparsed caseDef pattern: ${caseDef.pattern}" )
    ???

    
  def substIdent(using Quotes)(term: quotes.reflect.Term, 
                                 fromSym: quotes.reflect.Symbol, 
                                 toTerm: quotes.reflect.Term,
                                 owner: quotes.reflect.Symbol): quotes.reflect.Term = 
      import quotes.reflect._
      val argTransformer = new TreeMap() {
        override def transformTerm(tree: Term)(owner: Symbol):Term =
          tree match
            case Ident(name) if tree.symbol == fromSym => toTerm
            case _ => super.transformTerm(tree)(owner)
      }
      argTransformer.transformTerm(term)(owner)


    
