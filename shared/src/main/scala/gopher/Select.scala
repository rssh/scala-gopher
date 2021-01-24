package gopher

import cps._

import scala.quoted._
import scala.compiletime._
import scala.concurrent.duration._


class Select[F[_]:CpsSchedulingMonad](api: Gopher[F]):

  inline def apply[A](inline pf: PartialFunction[Any,A]): A =
    ${  
      Select.onceImpl[F,A]('pf, 'api )  
     }    

  def group[S]: SelectGroup[F,S] = new SelectGroup[F,S](api)   

  def once[S]: SelectGroup[F,S] = new SelectGroup[F,S](api)   

  def loop: SelectLoop[F] = new SelectLoop[F](api)

  def fold[S](s0:S)(step: (S,SelectGroup[F,S|SelectFold.Done[S]])=> S | SelectFold.Done[S]): S = {
      var s: S = s0
      while{
         val g = SelectGroup[F,S|SelectFold.Done[S]](api)
         step(s,g) match {
            case SelectFold.Done(r:S) => 
              s=r
              false
            case other =>
              s=other.asInstanceOf[S]
              true
         }
      } do ()
      s
  }

  def fold_async[S](s0:S)(step: (S,SelectGroup[F,S|SelectFold.Done[S]])=> F[S | SelectFold.Done[S]]): F[S] = {
    var g = SelectGroup[F,S|SelectFold.Done[S]](api)
    api.asyncMonad.flatMap(step(s0,g)){ s =>
       s match
        case SelectFold.Done(r) => api.asyncMonad.pure(r.asInstanceOf[S])
        case other => fold_async[S](other.asInstanceOf[S])(step)
    }
  }
  
  inline def afold[S](s0:S)(inline step: (S,SelectGroup[F,S|SelectFold.Done[S]])=> S | SelectFold.Done[S]) : F[S] =
    async[F](using api.asyncMonad).apply{
      fold(s0)(step)
    }
    
  //def map[A](step: PartialFunction[SelectGroup[F,A],A|SelectFold.Done[Unit]]): ReadChannel[F,A] =
  
  def map[A](step: SelectGroup[F,A] => A): ReadChannel[F,A] =
    mapAsync[A](x => api.asyncMonad.pure(step(x)))

  def mapAsync[A](step: SelectGroup[F,A] => F[A]): ReadChannel[F,A] =
    val r = makeChannel[A]()(using api)
    api.asyncMonad.spawn{
      async{
        var done = false
        while(!done) 
          val g = SelectGroup[F,A](api)
          try {
            val e = await(step(g))
            r.write(e)
          } catch { 
            case ex: ChannelClosedException =>
              r.close()
              done=true
          }
      }
    }
    r

  def forever: SelectForever[F] = new SelectForever[F](api)

  inline def aforever(inline pf: PartialFunction[Any,Unit]): F[Unit] =
    async {
      val runner = new SelectForever[F](api)
      runner.apply(pf)
    }

    
  


object Select:

  sealed trait SelectGroupExpr[F[_],S]:
    def  toExprOf[X <: SelectListeners[F,S]]: Expr[X]

  sealed trait SelectorCaseExpr[F[_]:Type, S:Type]:
     type Monad[X] = F[X]
     def appended[L <: SelectListeners[F,S] : Type](base: Expr[L])(using Quotes): Expr[L]

  case class ReadExpression[F[_]:Type, A:Type, S:Type](ch: Expr[ReadChannel[F,A]], f: Expr[A => S]) extends SelectorCaseExpr[F,S]:
     def appended[L <: SelectListeners[F,S]: Type](base: Expr[L])(using Quotes): Expr[L] =
       '{  $base.onRead($ch)($f) }
       
  case class WriteExpression[F[_]:Type, A:Type, S:Type](ch: Expr[WriteChannel[F,A]], a: Expr[A], f: Expr[A => S]) extends SelectorCaseExpr[F,S]:
      def appended[L <: SelectListeners[F,S]: Type](base: Expr[L])(using Quotes): Expr[L] =
      '{  $base.onWrite($ch,$a)($f) }
   
  case class TimeoutExpression[F[_]:Type,S:Type](t: Expr[FiniteDuration], f: Expr[ FiniteDuration => S ]) extends SelectorCaseExpr[F,S]:
      def appended[L <: SelectListeners[F,S]: Type](base: Expr[L])(using Quotes): Expr[L] =
       '{  $base.onTimeout($t)($f) }
    
  case class DoneExression[F[_]:Type, A:Type, S:Type](ch: Expr[ReadChannel[F,A]], f: Expr[Unit=>S]) extends SelectorCaseExpr[F,S]:
      def appended[L <: SelectListeners[F,S]: Type](base: Expr[L])(using Quotes): Expr[L] =
        '{  $base.onRead($ch.done)($f) }

  def onceImpl[F[_]:Type, A:Type](pf: Expr[PartialFunction[Any,A]], api: Expr[Gopher[F]])(using Quotes): Expr[A] =
       def builder(caseDefs: List[SelectorCaseExpr[F,A]]):Expr[A] = {
          val s0 = '{
             new SelectGroup[F,A]($api)(using ${api}.asyncMonad)
          }
          val g: Expr[SelectGroup[F,A]] = caseDefs.foldLeft(s0){(s,e) =>
             e.appended(s)
          }
          val r = '{ $g.run() }
          r.asExprOf[A]
       }
       runImpl( builder, pf)

  def loopImpl[F[_]:Type](pf: Expr[PartialFunction[Any,Boolean]], api: Expr[Gopher[F]])(using Quotes): Expr[Unit] =
      def builder(caseDefs: List[SelectorCaseExpr[F,Boolean]]):Expr[Unit] = {
          val s0 = '{
              new SelectLoop[F]($api)(using ${api}.asyncMonad)
          }
          val g: Expr[SelectLoop[F]] = caseDefs.foldLeft(s0){(s,e) =>
              e.appended(s)
          }
          val r = '{ $g.run() }
          r.asExprOf[Unit]
      }
      runImpl( builder, pf)
      

  def foreverImpl[F[_]:Type](pf: Expr[PartialFunction[Any,Unit]], api:Expr[Gopher[F]])(using Quotes): Expr[Unit] =
      def builder(caseDefs: List[SelectorCaseExpr[F,Unit]]):Expr[Unit] = {
          val s0 = '{
              new SelectForever[F]($api)(using ${api}.asyncMonad)
          }
          val g: Expr[SelectForever[F]] = caseDefs.foldLeft(s0){(s,e) =>
              e.appended(s)
          }
          val r = '{ $g.run() }
          r.asExprOf[Unit]
      }
      runImpl(builder, pf)



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

    val caseDefGuard = parseCaseDefGuard(caseDef)

    def handleRead(bind: Bind, valName: String, channel:Term, tp:TypeRepr): SelectorCaseExpr[F,S] =
      val readFun = makeLambda(valName,tp,bind.symbol,caseDef.rhs)
      if (channel.tpe <:< TypeRepr.of[ReadChannel[F,?]]) 
        tp.asType match
          case '[a] => ReadExpression(channel.asExprOf[ReadChannel[F,a]],readFun.asExprOf[a=>S])
          case _ => 
            reportError("can't determinate read type", caseDef.pattern.asExpr)
      else
        reportError("read pattern is not a read channel", channel.asExpr)
  
    def handleWrite(bind: Bind, valName: String, channel:Term, tp:TypeRepr): SelectorCaseExpr[F,S] =   
      val writeFun = makeLambda(valName,tp, bind.symbol, caseDef.rhs)
      val e = caseDefGuard.getOrElse(valName,
        reportError(s"not found binding ${valName} in write condition", caseDef.pattern.asExpr)
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
                              quotes.reflect.Select(chobj,nameReadOrWrite),
                              "unapply"),targs),
                            impl,List(b@Bind(e,ePat),Bind(ch,chPat))) =>
            if (chobj.tpe == '{gopher.Channel}.asTerm.tpe)   
              val chExpr = caseDefGuard.getOrElse(ch,reportError(s"select condition for ${ch} is not found",caseDef.pattern.asExpr))
              nameReadOrWrite match
                case "Read" =>
                  val elementType = extractType("read",chExpr, ePat)
                  handleRead(b,e,chExpr,elementType)
                case "Write" =>
                  val elementType = extractType("write",chExpr, ePat)
                  handleWrite(b,e,chExpr,elementType)
                case _ =>
                  reportError(s"Read or Write expected, we have ${nameReadOrWrite}", caseDef.pattern.asExpr)
            else
              reportError("Incorrect select pattern, expected or x:channel.{read,write} or Channel.{Read,Write}",chobj.asExpr)
      case _ =>
        report.error(
          s"""
              expected one of: 
                     v: channel.read
                     v: channel.write if v == expr
                     v: Time.after if v == expr
              we have
                    ${caseDef.pattern.show}
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

  

