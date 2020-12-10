package gopher

import cps._

import scala.quoted._
import scala.compiletime._

class Select[F[_]:CpsSchedulingMonad](api: Gopher[F]):

  inline def apply[A](inline pf: PartialFunction[Any,A]): A =
    ${  
      Select.onceImpl[F,A]('pf, '{summonInline[CpsSchedulingMonad[F]]} )  
     }    
     


object Select:

  sealed trait SelectGroupExpr[F[_],S]:
    def  toExpr: Expr[SelectGroup[F,S]]

  sealed trait SelectorCaseExpr[F[_]:Type, S:Type]:
     type Monad[X] = F[X]
     def appended(base: Expr[SelectGroup[F,S]])(using Quotes): Expr[SelectGroup[F,S]]

  case class ReadExpression[F[_]:Type, A:Type, S:Type](ch: Expr[ReadChannel[F,A]], f: Expr[A => S]) extends SelectorCaseExpr[F,S]:
     def appended(base: Expr[SelectGroup[F,S]])(using Quotes): Expr[SelectGroup[F,S]] =
       '{  $base.reading($ch)($f) }
       
  case class WriteExpression[F[_]:Type, A:Type, S:Type](ch: Expr[WriteChannel[F,A]], a: Expr[A], f: Expr[A => S]) extends SelectorCaseExpr[F,S]:
      def appended(base: Expr[SelectGroup[F,S]])(using Quotes): Expr[SelectGroup[F,S]] =
      '{  $base.writing($ch,$a)($f) }
   
  case class DefaultExpression[F[_]:Type,S:Type](ch: Expr[ () => S ]) extends SelectorCaseExpr[F,S]:
      def appended(base: Expr[SelectGroup[F,S]])(using Quotes): Expr[SelectGroup[F,S]] =
       '{  ??? }
    
  

  def onceImpl[F[_]:Type, A:Type](pf: Expr[PartialFunction[Any,A]], m: Expr[CpsSchedulingMonad[F]])(using Quotes): Expr[A] =
    import quotes.reflect._
    onceImplTree[F,A](Term.of(pf), m).asExprOf[A]

  def onceImplTree[F[_]:Type, S:Type](using Quotes)(pf: quotes.reflect.Term, m: Expr[CpsSchedulingMonad[F]]): quotes.reflect.Term =
    import quotes.reflect._
    pf match
      case Lambda(valDefs, body) =>
        onceImplTree[F,S](body, m)
      case Inlined(_,List(),body) => 
        onceImplTree[F,S](body, m)
      case Match(scrutinee,cases) =>
        //val caseExprs = cases map(x => parseCaseDef[F,A](x))
        //if (caseExprs.find(_.isInstanceOf[DefaultExpression[?]]).isDefined) {
        //  report.error("default is not supported")
        //}
        val s0 = '{
            new SelectGroup[F,S](using $m)
          }
        val g: Expr[SelectGroup[F,S]] = cases.foldLeft(s0){(s,e) =>
           parseCaseDef(e).appended(s)
        }
        val r = '{ $g.run }
        Term.of(r)
    

  def parseCaseDef[F[_]:Type,S:Type](using Quotes)(caseDef: quotes.reflect.CaseDef): SelectorCaseExpr[F,S] =
    import quotes.reflect._
    caseDef.pattern match 
      case Inlined(_,List(),body) => 
            parseCaseDef(CaseDef(body, caseDef.guard, caseDef.rhs))
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
      case b@Bind(v, tp@Typed(expr, TypeSelect(ch,"write"))) =>
          val mt = MethodType(List(v))(_ => List(tp.tpe), _ => caseDef.rhs.tpe)
          //val newSym = Symbol.newVal(Symbol.spliceOwner,v,tp.tpe.widen,Flags.EmptyFlags, Symbol.noSymbol)
          //val newIdent = Ref(newSym)
          val writeFun = Lambda(Symbol.spliceOwner,mt, (owner,args) => 
                                    substIdent(caseDef.rhs,b.symbol, args.head.asInstanceOf[Term], owner).changeOwner(owner))    
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
            case None =>
              // are we have shadowed symbol with the same name?
              //  mb try to find one ?
              report.error("condition in write is required")
              throw new RuntimeException("condition in wrte is required")
          if (ch.tpe <:< TypeRepr.of[WriteChannel[F,?]]) then
            tp.tpe.asType match
              case '[a] => 
                WriteExpression(ch.asExprOf[WriteChannel[F,a]],e.asExprOf[a], writeFun.asExprOf[a=>S]) 
              case _ =>
                report.error("Can't determinate type of write", tp.asExpr) 
                throw new RuntimeException("Macro error")
          else
            report.error("Write channel expected", ch.asExpr)
            throw new RuntimeException("not a write channel")
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


    
