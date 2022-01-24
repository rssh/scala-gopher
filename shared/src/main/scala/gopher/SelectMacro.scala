package gopher


import cps._

import scala.quoted._
import scala.compiletime._
import scala.concurrent.duration._
import scala.util.control.NonFatal




object SelectMacro:

  import cps.macros.forest.TransformUtil

  sealed trait SelectGroupExpr[F[_],S, R]:
    def  toExprOf[X <: SelectListeners[F,S, R]]: Expr[X]

  sealed trait SelectorCaseExpr[F[_]:Type, S:Type, R:Type]:
     type Monad[X] = F[X]
     def appended[L <: SelectListeners[F,S,R] : Type](base: Expr[L])(using Quotes): Expr[L]

  case class ReadExpression[F[_]:Type, A:Type, S:Type, R:Type](ch: Expr[ReadChannel[F,A]], f: Expr[A => S], isDone: Boolean) extends SelectorCaseExpr[F,S,R]:
     def appended[L <: SelectListeners[F,S,R]: Type](base: Expr[L])(using Quotes): Expr[L] =
       '{  $base.onRead($ch)($f) }
       
  case class WriteExpression[F[_]:Type, A:Type, S:Type, R:Type](ch: Expr[WriteChannel[F,A]], a: Expr[A], f: Expr[A => S]) extends SelectorCaseExpr[F,S,R]:
      def appended[L <: SelectListeners[F,S,R]: Type](base: Expr[L])(using Quotes): Expr[L] =
      '{  $base.onWrite($ch,$a)($f) }
   
  case class TimeoutExpression[F[_]:Type,S:Type, R:Type](t: Expr[FiniteDuration], f: Expr[ FiniteDuration => S ]) extends SelectorCaseExpr[F,S,R]:
      def appended[L <: SelectListeners[F,S,R]: Type](base: Expr[L])(using Quotes): Expr[L] =
       '{  $base.onTimeout($t)($f) }
    
  case class DoneExression[F[_]:Type, A:Type, S:Type, R:Type](ch: Expr[ReadChannel[F,A]], f: Expr[Unit=>S]) extends SelectorCaseExpr[F,S,R]:
      def appended[L <: SelectListeners[F,S,R]: Type](base: Expr[L])(using Quotes): Expr[L] =
        '{  $base.onRead($ch.done)($f) }


  def selectListenerBuilder[F[_]:Type, S:Type, R:Type, L <: SelectListeners[F,S,R]:Type](
      constructor: Expr[L], 
      caseDefs: List[SelectorCaseExpr[F,S,R]])(using Quotes): Expr[L] =
        val s0 = constructor
        caseDefs.foldLeft(s0){(s,e) =>
          e.appended(s)
        }
        

  def buildSelectListenerRun[F[_]:Type, S:Type, R:Type, L <: SelectListeners[F,S,R]:Type](
           constructor: Expr[L], 
           caseDefs: List[SelectorCaseExpr[F,S,R]], 
           api:Expr[Gopher[F]],
           monadContext: Expr[CpsMonadContext[F]],
           )(using Quotes): Expr[R] =
            val g = selectListenerBuilder(constructor, caseDefs)
            //  dotty bug if g.run
            val r = '{ await($g.runAsync())(using ${api}.asyncMonad, $monadContext) } 
            r.asExprOf[R]

  def buildSelectListenerRunAsync[F[_]:Type, S:Type, R:Type, L <: SelectListeners[F,S,R]:Type](
              constructor: Expr[L], 
              caseDefs: List[SelectorCaseExpr[F,S,R]], 
              api:Expr[Gopher[F]])(using Quotes): Expr[F[R]] =
                val g = selectListenerBuilder(constructor, caseDefs)
                //  dotty bug if g.run
                val r = '{ $g.runAsync() } 
                r.asExprOf[F[R]]
   
   

  def onceImpl[F[_]:Type, A:Type](pf: Expr[PartialFunction[Any,A]], api: Expr[Gopher[F]], monadContext: Expr[CpsMonadContext[F]])(using Quotes): Expr[A] =
       def builder(caseDefs: List[SelectorCaseExpr[F,A,A]]):Expr[A] = {
          val s0 = '{
             new SelectGroup[F,A]($api)
          }
          buildSelectListenerRun(s0, caseDefs, api, monadContext)
       }
       runImpl(builder, pf)

  def loopImpl[F[_]:Type](pf: Expr[PartialFunction[Any,Boolean]], api: Expr[Gopher[F]], monadContext: Expr[CpsMonadContext[F]])(using Quotes): Expr[Unit] =
      def builder(caseDefs: List[SelectorCaseExpr[F,Boolean,Unit]]):Expr[Unit] = {
          val s0 = '{
              new SelectLoop[F]($api)
          }
          buildSelectListenerRun(s0, caseDefs, api, monadContext)
      }
      runImpl( builder, pf)
      

  def foreverImpl[F[_]:Type](pf: Expr[PartialFunction[Any,Unit]], api:Expr[Gopher[F]], monadContext: Expr[CpsMonadContext[F]])(using Quotes): Expr[Unit] =
      def builder(caseDefs: List[SelectorCaseExpr[F,Unit,Unit]]):Expr[Unit] = {
          val s0 = '{
              new SelectForever[F]($api)
          }
          buildSelectListenerRun(s0, caseDefs, api, monadContext)
      }
      runImpl(builder, pf)

  def aforeverImpl[F[_]:Type](pf: Expr[PartialFunction[Any,Unit]], api:Expr[Gopher[F]])(using Quotes): Expr[F[Unit]] =
    import quotes.reflect._
    def builder(caseDefs: List[SelectorCaseExpr[F,Unit,Unit]]):Expr[F[Unit]] = {
      val s0 = '{
          new SelectForever[F]($api)
      }
      buildSelectListenerRunAsync(s0, caseDefs, api)
    }
    runImplTree(builder, pf.asTerm)
  

  def runImpl[F[_]:Type, A:Type,B :Type](builder: List[SelectorCaseExpr[F,A,B]]=>Expr[B],
                                 pf: Expr[PartialFunction[Any,A]])(using Quotes): Expr[B] =
    import quotes.reflect._
    runImplTree[F,A,B,B](builder, pf.asTerm)

  def runImplTree[F[_]:Type, A:Type, B:Type, C:Type](using Quotes)(
                builder: List[SelectorCaseExpr[F,A,B]] => Expr[C],
                pf: quotes.reflect.Term
                ): Expr[C] =
    import quotes.reflect._
    pf match
      case Lambda(valDefs, body) =>
        runImplTree[F,A,B,C](builder, body)
      case Inlined(_,List(),body) => 
        runImplTree[F,A,B,C](builder, body)
      case Match(scrutinee,cases) =>
        //val caseExprs = cases map(x => parseCaseDef[F,A](x))
        //if (caseExprs.find(_.isInstanceOf[DefaultExpression[?]]).isDefined) {
        //  report.error("default is not supported")
        //}
        val unorderedCases = cases.map(parseCaseDef[F,A,B](_))
        // done should be 
        val (isDone,notDone) = unorderedCases.partition{ x =>
          x match
            case DoneExression(_,_) => true
            case ReadExpression(_,_,isDone) => isDone
            case _ => false
        }
        val doneFirstCases = isDone ++ notDone
        builder(doneFirstCases)
    

  def parseCaseDef[F[_]:Type,S:Type,R:Type](using Quotes)(caseDef: quotes.reflect.CaseDef): SelectorCaseExpr[F,S,R] =
    import quotes.reflect._

    val caseDefGuard = parseCaseDefGuard(caseDef)

    def handleRead(bind: Bind, valName: String, channel:Term, tp:TypeRepr): SelectorCaseExpr[F,S,R] =
      val readFun = makeLambda(valName,tp,bind.symbol,caseDef.rhs)
      if (channel.tpe <:< TypeRepr.of[ReadChannel[F,?]]) 
        tp.asType match
          case '[a] => 
            val isDone = channel match
              case quotes.reflect.Select(ch1,"done") if (ch1.tpe <:< TypeRepr.of[ReadChannel[F,?]]) => true
              case _ => false
            ReadExpression(channel.asExprOf[ReadChannel[F,a]],readFun.asExprOf[a=>S],isDone)
          case _ => 
            reportError("can't determinate read type", caseDef.pattern.asExpr)
      else
        reportError("read pattern is not a read channel", channel.asExpr)
  
    def handleWrite(bind: Bind, valName: String, channel:Term, tp:TypeRepr): SelectorCaseExpr[F,S,R] =   
      val writeFun = makeLambda(valName,tp, bind.symbol, caseDef.rhs)
      val e = caseDefGuard.getOrElse(valName,
        reportError(s"not found binding ${valName} in write condition", channel.asExpr)
      )
      if (channel.tpe <:< TypeRepr.of[WriteChannel[F,?]]) then
        tp.asType match
          case '[a] => 
            WriteExpression(channel.asExprOf[WriteChannel[F,a]],e.asExprOf[a], writeFun.asExprOf[a=>S]) 
          case _ =>
            reportError("Can't determinate type of write", caseDef.pattern.asExpr) 
      else
        reportError("Write channel expected", channel.asExpr)

    def extractType[F[_]:Type](name: "read"|"write", channelTerm: Term, pat: Tree): TypeRepr =
      import quotes.reflect._
      pat match
        case Typed(_,tp) => tp.tpe
        case _ =>
            TypeSelect(channelTerm,name).tpe
     
    def handleUnapply(chObj: Term, nameReadOrWrite: String, bind: Bind, valName: String, ePat: Tree, ch: String): SelectorCaseExpr[F,S,R] =
      import quotes.reflect._
      if (chObj.tpe == '{gopher.Channel}.asTerm.tpe)   
        val chExpr = caseDefGuard.getOrElse(ch,reportError(s"select condition for ${ch} is not found",caseDef.pattern.asExpr))
        nameReadOrWrite match
          case "Read" =>
            val elementType = extractType("read",chExpr, ePat)
            handleRead(bind,valName,chExpr,elementType)
          case "Write" =>
            val elementType = extractType("write",chExpr, ePat)
            handleWrite(bind,valName,chExpr,elementType)
          case _ =>
            reportError(s"Read or Write expected, we have ${nameReadOrWrite}", caseDef.pattern.asExpr)
      else
        reportError("Incorrect select pattern, expected or x:channel.{read,write} or Channel.{Read,Write}",chObj.asExpr)

    def safeShow(t:Tree): String =
      try 
        t.show
      catch
        case NonFatal(ex) => 
          ex.printStackTrace()
          s"(exception durign show:${ex.getMessage()})"

    caseDef.pattern match 
      case Inlined(_,List(),body) => 
            parseCaseDef(CaseDef(body, caseDef.guard, caseDef.rhs))
      case b@Bind(v, tp@Typed(expr, TypeSelect(ch,"read"))) =>
            handleRead(b,v,ch,tp.tpe)
      case b@Bind(v, tp@Typed(expr, Annotated(TypeSelect(ch,"read"),_))) =>
            handleRead(b,v,ch,tp.tpe)
      case tp@Typed(expr, TypeSelect(ch,"read")) =>
              // todo: introduce 'dummy' val
              reportError("binding var in read expression is mandatory", caseDef.pattern.asExpr)
      case b@Bind(v, tp@Typed(expr, TypeSelect(ch,"write"))) =>
            handleWrite(b,v,ch,tp.tpe)
      case b@Bind(v, tp@Typed(expr, Annotated(TypeSelect(ch,"write"),_))) =>
            handleWrite(b,v,ch,tp.tpe)
      case b@Bind(v, tp@Typed(expr, TypeSelect(ch,"after"))) =>
          val timeoutFun = makeLambda(v, tp.tpe, b.symbol, caseDef.rhs)
          val e = caseDefGuard.getOrElse(v, reportError(s"can't find condifion for $v",caseDef.pattern.asExpr))
          if (ch.tpe <:< TypeRepr.of[gopher.Time] ||  ch.tpe <:< TypeRepr.of[gopher.Time.type]) 
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
      case pat@Unapply(TypeApply(quotes.reflect.Select(
                              quotes.reflect.Select(chObj,nameReadOrWrite),
                              "unapply"),targs),
                            impl,List(b@Bind(e,ePat),Bind(ch,chPat))) =>
            handleUnapply(chObj, nameReadOrWrite, b, e, ePat, ch)
      case pat@TypedOrTest(Unapply(TypeApply(quotes.reflect.Select(
                quotes.reflect.Select(chobj,nameReadOrWrite),
                "unapply"),targs),
              impl,List(b@Bind(e,ePat),Bind(ch,chPat))),a) =>
            handleUnapply(chobj, nameReadOrWrite, b, e, ePat, ch)    
      case _ =>
        report.error(
          s"""
              expected one of: 
                     v: channel.read
                     v: channel.write if v == expr
                     v: Time.after if v == expr
              we have
                    ${safeShow(caseDef.pattern)}
                    (tree:  ${caseDef.pattern})
          """, caseDef.pattern.asExpr)
        reportError(s"unparsed caseDef pattern: ${caseDef.pattern}", caseDef.pattern.asExpr)
        
  end parseCaseDef


  def parseCaseDefGuard(using Quotes)(caseDef: quotes.reflect.CaseDef): Map[String,quotes.reflect.Term] =
      import quotes.reflect._
      caseDef.guard match
        case Some(condition) =>
          parseSelectCondition(condition, Map.empty)
        case None =>
          Map.empty
        

  def parseSelectCondition(using Quotes)(condition: quotes.reflect.Term, 
                        entries:Map[String,quotes.reflect.Term]): Map[String,quotes.reflect.Term] =
      import quotes.reflect._
      condition match
          case Apply(quotes.reflect.Select(Ident(v1),"=="),List(expr)) =>
            entries.updated(v1, expr)
          case Apply(quotes.reflect.Select(frs, "&&" ), List(snd)) =>
            parseSelectCondition(snd, parseSelectCondition(frs, entries)) 
          case _ =>
            reportError(
              s"""Invalid select guard form, expected one of
                     channelName == channelEpxr
                     writeBind == writeExpresion
                     condition && condition
                 we have
                    ${condition.show}
              """,
              condition.asExpr)
        

  def makeLambda(using Quotes)(argName: String, 
                argType: quotes.reflect.TypeRepr, 
                oldArgSymbol: quotes.reflect.Symbol,
                body: quotes.reflect.Term): quotes.reflect.Term =
    import quotes.reflect._
    val widenReturnType = TransformUtil.veryWiden(body.tpe)
    val mt = MethodType(List(argName))(_ => List(argType.widen), _ => widenReturnType)
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

  

