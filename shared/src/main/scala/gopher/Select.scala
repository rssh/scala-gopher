package gopher

import cps._

import scala.quoted._
import scala.compiletime._
import scala.concurrent.duration._

class Select[F[_]:CpsSchedulingMonad](api: Gopher[F]):

  inline def apply[A](inline pf: PartialFunction[Any,A]): A =
    ${  
      Select.onceImpl[F,A]('pf, '{summonInline[CpsSchedulingMonad[F]]}, 'api )  
     }    

  def group[S]: SelectGroup[F,S] = new SelectGroup[F,S](api)   

  def loop: SelectLoop[F] = new SelectLoop[F](api)

object Select:

  sealed trait SelectGroupExpr[F[_],S]:
    def  toExpr: Expr[SelectGroup[F,S]]

  sealed trait SelectorCaseExpr[F[_]:Type, S:Type]:
     type Monad[X] = F[X]
     def appended(base: Expr[SelectGroup[F,S]])(using Quotes): Expr[SelectGroup[F,S]]

  case class ReadExpression[F[_]:Type, A:Type, S:Type](ch: Expr[ReadChannel[F,A]], f: Expr[A => S]) extends SelectorCaseExpr[F,S]:
     def appended(base: Expr[SelectGroup[F,S]])(using Quotes): Expr[SelectGroup[F,S]] =
       '{  $base.onRead($ch)($f) }
       
  case class WriteExpression[F[_]:Type, A:Type, S:Type](ch: Expr[WriteChannel[F,A]], a: Expr[A], f: Expr[A => S]) extends SelectorCaseExpr[F,S]:
      def appended(base: Expr[SelectGroup[F,S]])(using Quotes): Expr[SelectGroup[F,S]] =
      '{  $base.onWrite($ch,$a)($f) }
   
  case class TimeoutExpression[F[_]:Type,S:Type](t: Expr[FiniteDuration], f: Expr[ FiniteDuration => S ]) extends SelectorCaseExpr[F,S]:
      def appended(base: Expr[SelectGroup[F,S]])(using Quotes): Expr[SelectGroup[F,S]] =
       '{  $base.onTimeout($t)($f) }
    
  case class DoneExression[F[_]:Type, A:Type, S:Type](ch: Expr[ReadChannel[F,A]], f: Expr[Unit=>S]) extends SelectorCaseExpr[F,S]:
      def appended(base: Expr[SelectGroup[F,S]])(using Quotes): Expr[SelectGroup[F,S]] =
        '{  $base.onRead($ch.done)($f) }

  def onceImpl[F[_]:Type, A:Type](pf: Expr[PartialFunction[Any,A]], m: Expr[CpsSchedulingMonad[F]], api: Expr[Gopher[F]])(using Quotes): Expr[A] =
       def builder(caseDefs: List[SelectorCaseExpr[F,A]]):Expr[A] = {
          val s0 = '{
             new SelectGroup[F,A]($api)(using $m)
          }
          val g: Expr[SelectGroup[F,A]] = caseDefs.foldLeft(s0){(s,e) =>
             e.appended(s)
          }
          val r = '{ $g.run() }
          r.asExprOf[A]
       }
       runImpl( builder, pf)
  

  def runImpl[F[_]:Type, A:Type,B :Type](builder: List[SelectorCaseExpr[F,A]]=>Expr[B],
                                 pf: Expr[PartialFunction[Any,A]])(using Quotes): Expr[B] =
    import quotes.reflect._
    runImplTree[F,A,B](builder, pf.asTerm)

  def runImplTree[F[_]:Type, A:Type, B:Type](using Quotes)(
                builder: List[SelectorCaseExpr[F,A]] => Expr[B],
                pf: quotes.reflect.Term
                ): Expr[B] =
    import quotes.reflect._
    pf match
      case Lambda(valDefs, body) =>
        runImplTree[F,A,B](builder, body)
      case Inlined(_,List(),body) => 
        runImplTree[F,A,B](builder, body)
      case Match(scrutinee,cases) =>
        //val caseExprs = cases map(x => parseCaseDef[F,A](x))
        //if (caseExprs.find(_.isInstanceOf[DefaultExpression[?]]).isDefined) {
        //  report.error("default is not supported")
        //}
        builder(cases.map(parseCaseDef[F,A](_)))
    

  def parseCaseDef[F[_]:Type,S:Type](using Quotes)(caseDef: quotes.reflect.CaseDef): SelectorCaseExpr[F,S] =
    import quotes.reflect._
    caseDef.pattern match 
      case Inlined(_,List(),body) => 
            parseCaseDef(CaseDef(body, caseDef.guard, caseDef.rhs))
      case b@Bind(v, tp@Typed(expr, TypeSelect(ch,"read"))) =>
          val readFun = makeLambda(v,tp.tpe,b.symbol,caseDef.rhs)
          if (ch.tpe <:< TypeRepr.of[ReadChannel[F,?]]) 
            tp.tpe.asType match
              case '[a] => ReadExpression(ch.asExprOf[ReadChannel[F,a]],readFun.asExprOf[a=>S])
              case _ => 
                reportError("can't determinate read type", caseDef.pattern.asExpr)
          else
            reportError("read pattern is not a read channel", ch.asExpr)
      case b@Bind(v, tp@Typed(expr, TypeSelect(ch,"write"))) =>
          val writeFun = makeLambda(v,tp.tpe, b.symbol, caseDef.rhs)
          val e = matchCaseDefCondition(caseDef, v)
          if (ch.tpe <:< TypeRepr.of[WriteChannel[F,?]]) then
            tp.tpe.asType match
              case '[a] => 
                WriteExpression(ch.asExprOf[WriteChannel[F,a]],e.asExprOf[a], writeFun.asExprOf[a=>S]) 
              case _ =>
                reportError("Can't determinate type of write", tp.asExpr) 
          else
            reportError("Write channel expected", ch.asExpr)
      case b@Bind(v, tp@Typed(expr, TypeSelect(ch,"after"))) =>
          val timeoutFun = makeLambda(v, tp.tpe, b.symbol, caseDef.rhs)
          val e = matchCaseDefCondition(caseDef, v)
          if (ch.tpe =:= TypeRepr.of[gopher.Time]) 
             TimeoutExpression(e.asExprOf[FiniteDuration], timeoutFun.asExprOf[FiniteDuration => S])
          else
            reportError(s"Expected Time, we have ${ch.show}", ch.asExpr) 
      case b@Bind(v, tp@Typed(expr, TypeSelect(ch,"done"))) =>
          val readFun = makeLambda(v,tp.tpe,b.symbol,caseDef.rhs)
          tp.tpe.asType match
            case '[a] => 
                if (ch.tpe <:< TypeRepr.of[ReadChannel[F,a]]) then
                    DoneExression(ch.asExprOf[ReadChannel[F,a]],readFun.asExprOf[Unit=>S])
                else
                  reportError("done base is not a read channel", ch.asExpr) 
            case _ =>
                reportError("can't determinate read type", caseDef.pattern.asExpr)
            
            
      case _ =>
        report.error(
          s"""
              expected one of: 
                     v: channel.read
                     v: channel.write if v == expr
                     v: Time.after if v == expr
              we have
                    ${caseDef.pattern.show}
          """, caseDef.pattern.asExpr)
        reportError(s"unparsed caseDef pattern: ${caseDef.pattern}", caseDef.pattern.asExpr)
        
  end parseCaseDef

  def matchCaseDefCondition(using Quotes)(caseDef: quotes.reflect.CaseDef, v: String): quotes.reflect.Term =
    import quotes.reflect._
    caseDef.guard match
      case Some(condition) =>
        condition match
          case Apply(quotes.reflect.Select(Ident(v1),method),List(expr)) =>
            if (v1 != v) {
               reportError(s"write name mismatch ${v1}, expected ${v}", condition.asExpr)
            }
            // TODO: check that method is '==''
            expr
          case _ =>  
            reportError(s"Condition is not in form x==expr,${condition} ",condition.asExpr)
      case _ =>
        reportError(s"Condition is required ",caseDef.pattern.asExpr)
    

  def makeLambda(using Quotes)(argName: String, 
                argType: quotes.reflect.TypeRepr, 
                oldArgSymbol: quotes.reflect.Symbol,
                body: quotes.reflect.Term): quotes.reflect.Term =
    import quotes.reflect._
    val mt = MethodType(List(argName))(_ => List(argType), _ => body.tpe.widen)
    Lambda(Symbol.spliceOwner, mt, (owner,args) =>
      substIdent(body,oldArgSymbol, args.head.asInstanceOf[Term], owner).changeOwner(owner))

    
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

  def reportError(message: String, posExpr: Expr[?])(using Quotes): Nothing =
    import quotes.reflect._
    report.error(message, posExpr)
    throw new RuntimeException(s"Error in macro: $message")




    
