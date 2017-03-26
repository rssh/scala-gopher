package gopher.channels

import java.util.concurrent.atomic.AtomicInteger

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import gopher._
import gopher.util._

import scala.concurrent._
import scala.concurrent.duration._
import scala.annotation.unchecked._
import java.util.function.{BiConsumer => JBiConsumer}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


abstract class FoldSelectorBuilder[T](nCases:Int) extends SelectorBuilder[T]
{

   type R = T

   type HandleFunction[A] = (ExecutionContext, FlowTermination[T],A) => Future[T]


   def reading[A](ch: Input[A])(f: A=>T): FoldSelectorBuilder[T] =
        macro SelectorBuilder.readingImpl[A,T,FoldSelectorBuilder[T]]

   def readingFoldEffected[A](ch:Input[A],projIndex:Int)(f: A=>T): FoldSelectorBuilder[T] =
        macro FoldSelectorBuilderImpl.readingFoldEffected[A,T,FoldSelectorBuilder[T]]

   def readingFoldEffectedWithFlowTerminationAsync[A](ch:Input[A], i:Int,
                     f: (ExecutionContext, FlowTermination[T], A) => Future[T]
                    ): this.type =
   {
     handleFunctions(i)=f
     inputIndices.put(i,ch)
     selector.addReader[A](ch,normalizedDispatchReader[A])
     this
   }

   def readingWithFlowTerminationAsync[A](ch: Input[A],
                       f: (ExecutionContext, FlowTermination[T], A) => Future[T]):this.type =
   {
       withReader[A](ch, normalizedPlainReader(f,ch))
   }

   def writing[A](ch: Output[A],x:A)(f: A=>T): FoldSelectorBuilder[T] =
        macro SelectorBuilder.writingImpl[A,T,FoldSelectorBuilder[T]]

  def writingFoldEffected[A](ch: Output[A], projIndex: Int, x:A)(f: A=>T): FoldSelectorBuilder[T] =
        macro FoldSelectorBuilderImpl.writingFoldEffected[A,T,FoldSelectorBuilder[T]]


  @inline
  def writingFoldEffectedWithFlowTerminationAsync[A](ch:Output[A], x: =>A, i: Int,
                                                     f: (ExecutionContext, FlowTermination[T], A) => Future[T]
                                                    ): this.type = {
      handleFunctions(i)=f
      handleOutputVars(i) = (()=>x)
      outputIndices.put(i,ch)
      val dispathWrite = normalizedDispatchWriter[A]
      selector.addWriter(ch,dispathWrite)
      this
  }


  @inline
   def writingWithFlowTerminationAsync[A](ch:Output[A], x: =>A,
                 f: (ExecutionContext, FlowTermination[T], A) => Future[T]): this.type = {
        withWriter[A](ch, normalizedWriter(f,x,ch))
   }

   def timeout(t:FiniteDuration)(f: FiniteDuration => T): FoldSelectorBuilder[T] =
       macro SelectorBuilder.timeoutImpl[T,FoldSelectorBuilder[T]]

   @inline
   def timeoutWithFlowTerminationAsync(t:FiniteDuration,
                       f: (ExecutionContext, FlowTermination[T], FiniteDuration) => Future[T]
                       ): this.type =
        withTimeout(t){ sk => Some(f(ec,sk.flowTermination,t) map Function.const(sk)  ) }


  def idle(body:T): FoldSelectorBuilder[T] =
         macro SelectorBuilder.idleImpl[T,FoldSelectorBuilder[T]]


  val inputIndices: IntIndexedCounterReverse[Input[_]] = new IntIndexedCounterReverse(nCases)
  val outputIndices: IntIndexedCounterReverse[Output[_]] = new IntIndexedCounterReverse(nCases)
  val handleFunctions: Array[HandleFunction[_]] = new Array(nCases)
  val handleOutputVars: Array[ () => _ ] = new Array(nCases)

  def reregisterInputIndices(): Unit =
  {
    inputIndices.foreachIndex{(i,cr) =>
      if (cr.counter <= 0) {
        cr.counter += 1
        val input = inputIndices.get(i).get.value
        val typedInput: Input[input.read] = input.asInstanceOf[Input[input.read]]
        val reader = normalizedDispatchReader[input.read]
        val fun = selector.lockedRead(reader,typedInput,selector)
        typedInput.cbread(fun,selector)
      }
    }
  }

  def reregisterOutputIndices():Unit =
  {
    outputIndices.foreachIndex{(i,cw) =>
      if (cw.counter <= 0) {
        cw.counter += 1
        val output = outputIndices.get(i).get.value
        val typedOutput:Output[output.write] = output.asInstanceOf[Output[output.write]]
        val writer = normalizedDispatchWriter[output.write]
        val fun = selector.lockedWrite(writer,typedOutput,selector)
        typedOutput.cbwrite(fun,selector)
      }
    }
  }

  def reregisterIndices():Unit =
  {
    reregisterInputIndices()
    reregisterOutputIndices()
  }

  def normalizedPlainReader[A](f:HandleFunction[A], ch:Input[A]):ContRead.AuxF[A,T]=
  {
    def nf(prev:ContRead[A,T]):Option[ContRead.In[A]=>Future[Continuated[T]]] = Some{
      case ContRead.Value(a) =>  f(ec,selector,a) map { _ =>  ContRead[A,T](nf,ch,selector) }
      case ContRead.Skip => { Future successful ContRead[A,T](nf,ch,selector) }
      case ContRead.ChannelClosed => prev.flowTermination.throwIfNotCompleted(new ChannelClosedException())
        Never.future
      case ContRead.Failure(ex) => prev.flowTermination.throwIfNotCompleted(ex)
        Never.future
    }
    nf
  }

  def normalizedDispatchReader[A]:ContRead.AuxF[A,T]= {
    // return never, becouse next step is generated via reregisterInputIndi
    def nf(prev: ContRead[A, T]): Option[ContRead.In[A] => Future[Continuated[T]]] = {
      val ch = prev.channel //match {
                  //case fe: FoldSelectorEffectedInput[_,_] =>
                  //    System.err.println(s"normalizedEffectedReader:fromEffected ${fe.current} ${fe.index} fe=${fe}  locked=${selector.isLocked}")
                  //     fe.current
                  //case _ => prev.channel
      //}
      val i = inputIndices.refIndexOf(ch)
      //System.err.println(s"normalizedEffectedReader ch=$ch i=$i locked=${selector.isLocked}")
      if (i == -1)
        None
      else {
        inputIndices.values(i).counter -= 1
        val ff = handleFunctions(i).asInstanceOf[HandleFunction[A]]
        Some {
          case ContRead.Value(a) => ff(ec, selector, a) map { _ => reregisterIndices(); Never }
          case ContRead.Skip => {
            reregisterIndices()
            Future successful Never
          }
          case ContRead.ChannelClosed => prev.flowTermination.throwIfNotCompleted(new ChannelClosedException())
            Never.future
          case ContRead.Failure(ex) => prev.flowTermination.throwIfNotCompleted(ex)
            Never.future
        }
      }
    }
    nf
  }




  def normalizedDispatchWriter[A]:ContWrite.AuxF[A,T] =
  {
    prev => {
      val i = outputIndices.refIndexOf(prev.channel)
      if (i == -1)
        None
      else {
        outputIndices.values(i).counter -= 1
        val ff = handleFunctions(i).asInstanceOf[HandleFunction[A]]
        val xn = handleOutputVars(i).asInstanceOf[()=>A].apply()
        Some((xn,ff(ec,prev.flowTermination,xn) map { _ => reregisterIndices(); Never } ))
      }
    }
  }

  def normalizedWriter[A](f:HandleFunction[A],x: =>A, ch:Output[A]):ContWrite.AuxF[A,T]= {
    def nf(prev: ContWrite[A, T]): Option[(A, Future[Continuated[T]])] = {
      val xn = x
      Some(xn, f(ec, prev.flowTermination, xn) map (_ => ContWrite(nf, ch, this.selector)))
    }
    nf
  }


  def beforeRefresh(): Unit =
  {
  //  inputIndices.clear()
  //  outputIndices.clear()
  }



}

/**
 * Short name for use in fold signature 
 **/
class FoldSelect[T](sf:SelectFactory, nCases: Int) extends FoldSelectorBuilder[T](nCases)
{
   override def api = sf.api



}


class FoldSelectorBuilderImpl(override val c:Context) extends SelectorBuilderImpl(c) {

  import c.universe._


  class FoldActionGenerator(fp: FoldParse) extends ActionGenerator {

    override def genReading(builder: TermName, channel: Tree, param: ValDef, body: Tree): Tree = {
      require(!(channel.symbol eq null))
      if (fp.stateSelectRole.active) {
        if (channel.symbol == fp.stateVal.symbol) {
          val clearedChannel = Ident(fp.stateVal.name)
          q"${builder}.readingFoldEffected(${clearedChannel},0){$param => $body}"
        } else {
          defaultActionGenerator.genReading(builder, channel, param, body)
        }
      } else {
        fp.projectionsBySym.get(channel.symbol) match {
          case None => defaultActionGenerator.genReading(builder, channel, param, body)
          case Some(proj) =>
            val (ch, index) = genProjChannelIndex(fp,proj)
            q"${builder}.readingFoldEffected($ch,$index){$param => $body}"
        }
      }
    }

    override def genWriting(builder: TermName, channel: Tree, expr: Tree, param: ValDef, body: Tree): c.universe.Tree = {
      if (fp.stateSelectRole.active) {
        if (channel.symbol == fp.stateVal.symbol) {
          val clearedChannel = Ident(fp.stateVal.name)
          q"${builder}.writingFoldEffected($clearedChannel, 0, $expr){$param => $body}"
        } else {
          defaultActionGenerator.genWriting(builder, channel, expr, param, body)
        }
      } else {
        fp.projectionsBySym.get(channel.symbol) match {
          case None => defaultActionGenerator.genWriting(builder, channel, expr, param, body)
          case Some(proj) =>
            val (ch, index) = genProjChannelIndex(fp,proj)
            q"${builder}.writingFoldEffected($ch, $index, $expr){$param => $body}"
        }
      }
    }

    override def genDone(builder: TermName, channel: Tree, param: ValDef, body: Tree): Tree =
    {
      if (fp.stateSelectRole.active) {
        if (channel.symbol == fp.stateVal.symbol) {
          val clearedChannel = Ident(fp.stateVal.name)
          q"${builder}.onDoneFoldEffected($clearedChannel, 0){$param => $body}"
        } else {
          defaultActionGenerator.genDone(builder,channel,param,body)
        }
      } else {
        fp.projectionsBySym.get(channel.symbol) match {
          case None => defaultActionGenerator.genDone(builder, channel, param, body)
          case Some(proj) =>
            val (ch, index) = genProjChannelIndex(fp,proj)
            q"${builder}.onDoneFoldEffected($ch, $index){$param => $body}"
        }
      }
    }

    def genProjChannelIndex(fp:FoldParse, proj: (FoldParseProjection, Int)): (Tree,Int) =
    {
      val i = proj._2
      val ch = makeProj(fp.stateVal.name,i)
      (ch,i)
    }

  }

  /**
    * ```
    * selector.afold(s0) { (s, selector) =>
    * selector match {
    * case x1: in1.read => f1
    * case x2: in2.read => f2
    * case x3: out3.write if (x3==v) => f3
    * case _ : in,d
    * case _  => f4
    * }
    * }
    * ```
    * will be transformed to
    * {{{
    * var s = s0
    * val bn = new FoldSelector(3)
    * bn.reading(in1)(x1 => f1 map {s=_; s })
    * bn.reading(in2)(x2 => f2 map {s=_; s })
    * bn.writing(out3,v)(x2 => f2 map {s=_; s})
    * bn.idle(f4 map {s=_; s})
    * }}}
    *
    * also supported partial function syntax:
    *
    * {{{
    * selector.afold((0,1)){ 
    *    case ((x,y),s) => s match {
    *      case x1: in1.read => f1
    *      case x2: in2.read => f2
    *      case x3: out3.write if (x3==v) => f3
    *      case _  => f4
    *    }
    * }}}
    * will be transformed to:
    * {{{
    * var s = s0
    * val bn = new FoldSelector(3)
    * bn.reading(in1)(x1 => async{ val x = s._1;
    *                              val y = s._2;
    *                              s = f1;  s} })
    * bn.reading(in2)(x2 => { val x = s._1;
    *                         val y = s._2;
    *                         s=f2; s} })
    * bn.writing(out3,v[x/s._1;y/s._2])
    *                     (x2 => s=f2; s})
    * }}}
    *
    * Using channels as part of fold state:
    * {{{
    * selector.afold(ch){ case (ch,s) =>
    *   s match {
    *      case x: ch.read => generated.write(x)
    *                         ch.filter(_ % x == 0)
    *   }
    * }
    * }}}
    * will be transformed to
    * {{{
    * var s = ch
    * val bn = new FoldSelector
    * //val ef = new FoldSelectorEffectedInput(()=>s)
    * bn.readingFoldEffected(0)(x => async{ generated.write(x)
    *                            s.filter(_ % x == 0)})
    * }}}
    **/
  def afold[S: c.WeakTypeTag](s: c.Expr[S])(op: c.Expr[(S, FoldSelect[S]) => S]): c.Expr[Future[S]] = {
    val foldParse = parseFold(op)
    val sName = foldParse.stateVal.name
    val sNameStable = TermName(c.freshName("s"))
    val bn = TermName(c.freshName("fold"))
    val ncases = foldParse.selectCases.map(preTransformCaseDef(foldParse, bn, _, sNameStable))
    val tree = Block(
      atPos(s.tree.pos)(q"var $sName = ${s}") ::
        (q"val $sNameStable = $sName") ::
        q"val ${bn} = new gopher.channels.FoldSelect[${weakTypeOf[S]}](${c.prefix},${ncases.length})" ::
        //wrapInEffected(foldParse, bn, transformSelectMatch(bn, ncases, new FoldActionGenerator(foldParse))),
        transformSelectMatch(bn, ncases, new FoldActionGenerator(foldParse)),
        q"${bn}.go"
    )
    c.Expr[Future[S]](tree)
  }

  def fold[S: c.WeakTypeTag](s: c.Expr[S])(op: c.Expr[(S, FoldSelect[S]) => S]): c.Expr[S] =
    c.Expr[S](q"scala.async.Async.await(${afold(s)(op).tree})")

  sealed trait SelectRole {
    def active: Boolean

    def generateRefresh(selector: TermName, state: TermName, i: Int): Option[c.Tree]
  }

  object SelectRole {


    case object NoParticipate extends SelectRole {
      def active = false

      def generateRefresh(selector: TermName, state: TermName, i: Int) = None
    }

    case object Read extends SelectRole {
      def active = true

      def generateRefresh(selector: TermName, state: TermName, i: Int) =
        Some(q"$selector.inputIndices.put(${if (i < 0) 0 else i},${makeProj(state, i)})")
    }

    case object Write extends SelectRole {
      def active = true

      def generateRefresh(selector: TermName, state: TermName, i: Int) =
        Some(q"$selector.outputIndices.put(${if (i < 0) 0 else i},${makeProj(state, i)})")

    }

    case object Done extends SelectRole {
      def active = true

      def generateRefresh(selector: TermName, state: TermName, i: Int) =
        Some(q"$selector.doneIndices.put(${if (i < 0) 0 else i},${makeProj(state, i)})")

    }

  }


  case class FoldParseProjection(
                                  sym: c.Symbol,
                                  selectRole: SelectRole
                                )

  case class FoldParse(
                        stateVal: ValDef,
                        stateSelectRole: SelectRole,
                        projections: List[FoldParseProjection],
                        selectValName: c.TermName,
                        selectCases: List[CaseDef]
                      ) {
    lazy val projectionsBySym: Map[c.Symbol, (FoldParseProjection, Int)] =
      projections.zipWithIndex.foldLeft(Map[c.Symbol, (FoldParseProjection, Int)]()) { (s, e) =>
        s.updated(e._1.sym, e)
      }
  }

  def withProjAssignments(fp: FoldParse, patSymbol: Symbol, body: c.Tree): c.Tree = {
    val stateName = fp.stateVal.name
    val projAssignments = (fp.projections.zipWithIndex) map {
      case (FoldParseProjection(sym, usedInSelect), i) =>
        val pf = TermName("_" + (i + 1).toString)
        q"val ${sym.name.toTermName} = $stateName.$pf"
    }
    val projectedSymbols = fp.projections.map(_.sym).toSet
    val nbody = cleanIdentsSubstEffected(fp, body, projectedSymbols + fp.stateVal.symbol + patSymbol)
    if (projAssignments.isEmpty)
      nbody
    else {
      Block(projAssignments, cleanIdentsSubstEffected(fp, nbody, projectedSymbols))
    }
  }

  private def cleanIdentsSubstEffected(fp: FoldParse, tree: c.Tree, symbols: Set[Symbol]): Tree = {
    val tr = new Transformer {
      override def transform(tree: c.Tree): c.Tree =
        tree match {
          case Ident(s) => if (symbols.contains(tree.symbol)) {
            // create new tree without associated symbol.
            //(typer wil reassociate one).
            atPos(tree.pos)(Ident(s))
          } else {
            super.transform(tree)
          }
          case ValDef(m, s, rhs, lhs) => if (symbols.contains(tree.symbol)) {
            atPos(tree.pos)(ValDef(m, s, rhs, lhs))
            super.transform(tree)
          } else {
            super.transform(tree)
          }
          case _ => super.transform(tree)
        }
    }
    tr.transform(tree)
  }

  def substProj(foldParse: FoldParse, newName: c.TermName, body: c.Tree, substEffected: Boolean, debug: Boolean): c.Tree = {
    val projections = foldParse.projections
    val stateSymbol = foldParse.stateVal.symbol
    val pi = projections.map(_.sym).zipWithIndex.toMap
    //val sName = stateSymbol.name.toTermName
    val sName = newName
    val transformer = new Transformer() {
      override def transform(tree: Tree): Tree =
        tree match {
          case Ident(name) => pi.get(tree.symbol) match {
            case Some(n) =>
              if (substEffected && projections(n).selectRole.active) {
                //val proj = makeEffectedName(projections(n).sym)
                //atPos(tree.pos)(q"${proj}")
                tree // will be changed (fully elimitated) later by processing of actionGenerator in transformCaseDed
              } else {
                atPos(tree.pos)(makeProj(sName,n))
              }
            case None =>
              if (tree.symbol eq stateSymbol) {
                if (substEffected && foldParse.stateSelectRole.active) {
                  //val en = makeEffectedName(stateSymbol)
                  //atPos(tree.pos)(Ident(en))
                  tree
                } else {
                  atPos(tree.pos)(Ident(sName))
                }
              } else {
                super.transform(tree)
              }
          }
          case t@Typed(expr, tpt) =>
            tpt match {
              case tptt: TypeTree =>
                tptt.original match {
                  case Select(base, name) =>
                    //tptt.setOriginal(tranform(tptt.original))
                    Typed(expr, transform(tptt.original))
                  //val trOriginal = transform(tptt.original)
                  //Typed(expr,internal.setOriginal(tptt,trOriginal))
                  //Typed(expr,tq"${sName}.read")
                  case _ =>
                    super.transform(tree)
                }
              case _ =>
                super.transform(tree)
            }

          case _ => super.transform(tree)
        }
    }
    return transformer.transform(body)
  }

  def makeProj(name: TermName, n: Int): c.Tree =
  {
    if (n == -1) {
      q"${name}"
    }else {
      val proj = TermName("_" + (n + 1).toString)
      (q"${name}.${proj}")
    }
  }

   def preTransformCaseDefBody(fp:FoldParse, foldSelect: TermName, patSymbol: Symbol, body:c.Tree):c.Tree =
   {
     val sName = fp.stateVal.name
     val tmpName = TermName(c.freshName("tmp"))
     val refresh = refreshEffected(fp, foldSelect)
     val statements = List(
           q"val $tmpName = ${withProjAssignments(fp,patSymbol,body)}",
           q"$sName = $tmpName"
          ) ++
            refresh ++ List(
            q"$sName"
          )
     q"{..$statements}"
   }


   def beforeRefresh(foldSelect: TermName):c.Tree =
    q"${foldSelect}.beforeRefresh()"

   def refreshEffected(fp:FoldParse, foldSelect:TermName):List[c.Tree] =
   {
    if (fp.stateSelectRole.active) {
       beforeRefresh(foldSelect)::
         fp.stateSelectRole.generateRefresh(foldSelect, fp.stateVal.name,-1).get::Nil
    }else{
       val r = fp.projections.zipWithIndex.filter(_._1.selectRole.active).flatMap{ case (proj,i) =>
         proj.selectRole.generateRefresh(foldSelect,fp.stateVal.name,i)
       }
       if (r.nonEmpty) {
         beforeRefresh(foldSelect)::r
       }else{
         List()
       }
    }
   }


   def preTransformCaseDef(fp:FoldParse, foldSelect: TermName, cd:CaseDef,stableName:TermName):CaseDef =
   {
     val patSymbol = cd.pat.symbol

     val acceptor = new SelectCaseDefAcceptor[FoldParse,(Tree, Tree)] {


       override def onRead(s: FoldParse, v: TermName, ch: Tree, tp: Tree): (Tree, Tree) = {
         // TODO: parse something like ch.done.read
         if (fp.projections.indexWhere(_.sym.name == v) != -1) {
           //low-priority: implement shadowing instead abort
           c.abort(cd.pat.pos,"read variable shadow fold state")
         }
         (cd.pat,cd.guard)
       }

       override def onWrite(s: FoldParse, v: TermName, expression: Tree, ch: Tree, tp: Tree): (Tree, Tree) = {
         val idx = fp.projections.indexWhere(_.sym.name == v)
         if (idx != -1) {
           val newName = TermName(c.freshName("wrt"))
           val newPat = atPos(cd.pat.pos)(Bind(newName, Typed(Ident(v),tp)))
           if (!cd.guard.isEmpty) {
             c.abort(cd.pos, "guard must be empty");
           }
           val sName = fp.stateVal.name.toTermName
           val proj = TermName("_" + (idx + 1))
           val newGuard = q"${newName} == $sName.$proj"
           (newPat, newGuard)
         }else {
           (cd.pat, cd.guard)
         }
       }

       override def onSelectTimeout(s: FoldParse, v: TermName, select: Tree, tp: Tree): (Tree, Tree)       =
       {
  (cd.pat, cd.guard)
       }

       override def onIdle(s: FoldParse): (Tree, Tree) = {
         (cd.pat, cd.guard)
       }

       override def onDone(s: FoldParse, v: TermName, ch: Tree, tp: Tree): (Tree, Tree) = {
         if (fp.projections.indexWhere(_.sym.name == v) != -1) {
           c.abort(cd.pat.pos,"done variable shadow fold state")
         }
         (cd.pat, cd.guard)
       }

     }

     val (pat, guard) = acceptSelectCaseDefPattern(cd, fp, acceptor)

     val symName = fp.stateVal.symbol.name.toTermName
     atPos(cd.pos)(CaseDef(substProj(fp,stableName,pat,true,false),
                           substProj(fp,symName,guard,false,false),
                           preTransformCaseDefBody(fp,foldSelect,patSymbol,cd.body)))
   }

   def parseFold[S](op: c.Expr[(S,FoldSelect[S])=>S]): FoldParse = 
   {
    op.tree match {
       case Function(List(x,y),Match(choice,cases)) => 
                         val ValDef(_,yName,_,_) = y
                         if (choice.symbol != y.symbol) {
                            if (cases.length == 1) {
                                cases.head match {
                                 case CaseDef(Apply(TypeTree(),
                                                    List(Apply(TypeTree(),params),Bind(sel,_))),
                                              guard,
                                              Match(Ident(choice1),cases1)) =>
                                   if (sel == choice1) {
                                      val selectSymbols = retrieveSelectChannels(cases1)
                                      FoldParse(
                                         stateVal = x,
                                         stateSelectRole = selectSymbols.getOrElse(x.symbol,SelectRole.NoParticipate),
                                         projections = params map { x=> val sym = x.symbol
                                                         FoldParseProjection(sym,selectSymbols.getOrElse(sym,SelectRole.NoParticipate))
                                                       },
                                         selectValName = sel.toTermName,
                                         selectCases = cases1
                                      )
                                   } else {
                                      c.abort(op.tree.pos,"expected shap like {case (a,s) => s match { ... } }")
                                   }
                                 case _ =>
                                    c.abort(op.tree.pos,"match agains selector in pf is expected")
                                }
                            } else {
                                c.abort(op.tree.pos,"partial function in fold must have one case")
                            } 
                         } else {
                            val selectorName = choice match {
                              case Ident(sel) => sel
                            }
                            if (selectorName == yName) {
                              val selectSymbols = retrieveSelectChannels(cases)
                              FoldParse(
                                stateVal = x, 
                                stateSelectRole = selectSymbols.getOrElse(x.symbol,SelectRole.NoParticipate),
                                projections = List(),
                                selectValName = selectorName.toTermName, 
                                selectCases = cases
                              )
                            } else {
                              c.abort(op.tree.pos,"expected choice over selector in fold")
                            }
                         }
                       // TODO: check that 'choice' == 'y'
       case Function(params,something) =>
               c.abort(op.tree.pos,"match is expected in select.fold, we have: "+MacroUtil.shortString(c)(op.tree))
       case _ =>
               c.abort(op.tree.pos,"inline function is expected in select.fold, we have: "+MacroUtil.shortString(c)(op.tree))
    }
   }

   private def retrieveSelectChannels(cases:List[CaseDef]): Map[Symbol,SelectRole] =
   {
    val s0=Map[Symbol,SelectRole]()
    val acceptor = new SelectCaseDefAcceptor[Map[Symbol,SelectRole],Map[Symbol,SelectRole]] {
      override def onRead(s: Map[Symbol, SelectRole], v: TermName, ch: Tree, tp:Tree): Map[Symbol, SelectRole] =
        s.updated(ch.symbol,SelectRole.Read)

      override def onWrite(s: Map[c.Symbol, SelectRole], v: TermName, expr :c.Tree, ch: c.Tree, tp: Tree): Map[Symbol, SelectRole] =
        s.updated(ch.symbol,SelectRole.Write)

      override def onSelectTimeout(s: Map[Symbol, SelectRole], v: TermName, select: Tree, tp: Tree): Map[Symbol, SelectRole] = s

      override def onIdle(s: Map[Symbol, SelectRole]): Map[Symbol, SelectRole] = s

      override def onDone(s: Map[Symbol, SelectRole], v:TermName, ch: Tree, tp: Tree): Map[Symbol, SelectRole] =
        s.updated(ch.symbol,SelectRole.Done)
    }
    cases.foldLeft(s0){ (s,e) =>
      acceptSelectCaseDefPattern(e,s,acceptor)
    }
   }




  def readingFoldEffected[A,B:c.WeakTypeTag,S](ch:c.Expr[Input[A]], projIndex :c.Expr[Int])(f:c.Expr[A=>B]):c.Expr[S] =
  {
    import c.universe._
    f.tree match {
      case Function(valdefs, body) =>
        //val effectedName = ch.tree match {
        //  case Ident(name) => makeEffectedName(ch.tree.symbol)
        //  case _ => c.abort(f.tree.pos, s"Identifier expected, we have ${ch.tree}")
        //}

        SelectorBuilder.buildAsyncCall[B,S](c)(valdefs,body,
          { (nvaldefs, nbody) =>
            q"""${c.prefix}.readingFoldEffectedWithFlowTerminationAsync(${ch},${projIndex},
                                       ${Function(nvaldefs,nbody)}
                                      )
                                  """
          })
      case _ => c.abort(c.enclosingPosition,"argument of reading.apply must be function")
    }
  }

  def writingFoldEffected[A,T:c.WeakTypeTag,S](ch:c.Expr[Output[A]], projIndex: c.Expr[Int], x:c.Expr[A])(f:c.Expr[A=>T]):c.Expr[S] =
  {
    import c.universe._
    f.tree match {
      case Function(valdefs, body) =>
        val retval = SelectorBuilder.buildAsyncCall[T,S](c)(valdefs,body,
          { (nvaldefs, nbody) =>
            q"""${c.prefix}.writingFoldEffectedWithFlowTerminationAsync(${ch},${x}, ${projIndex},
                             ${Function(nvaldefs,nbody)}
                       )
                     """
          })
        retval
      case _ => c.abort(c.enclosingPosition,"second argument of writing must have shape Function(x,y)")
    }
  }




}


