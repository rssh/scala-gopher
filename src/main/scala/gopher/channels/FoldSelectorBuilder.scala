package gopher.channels

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import gopher._
import gopher.util._
import scala.concurrent._
import scala.concurrent.duration._
import scala.annotation.unchecked._



trait FoldSelectorBuilder[T] extends SelectorBuilder[T]
{

   def reading[A](ch: Input[A])(f: A=>T): FoldSelectorBuilder[T] =
        macro SelectorBuilder.readingImpl[A,T,FoldSelectorBuilder[T]]

   def readingWithFlowTerminationAsync[A](ch: Input[A], f: (ExecutionContext, FlowTermination[T], A) => Future[T] ): this.type =
   {
     def normalized(_cont: ContRead[A,T]):Option[ContRead.In[A]=>Future[Continuated[T]]] =
            Some(ContRead.liftIn(_cont){ a=>
                    f(ec,selector,a) map Function.const(ContRead(normalized,ch,selector))
                })
     withReader[A](ch, normalized) 
   }

   def writing[A](ch: Output[A],x:A)(f: A=>T): FoldSelectorBuilder[T] =
        macro SelectorBuilder.writingImpl[A,T,FoldSelectorBuilder[T]]


   @inline
   def writingWithFlowTerminationAsync[A](ch:Output[A], x: =>A, f: (ExecutionContext, FlowTermination[T], A) => Future[T] ): this.type =
       withWriter[A](ch,   { cw => Some(x,f(ec,cw.flowTermination, x) map Function.const(cw)) } )

   def timeout(t:FiniteDuration)(f: FiniteDuration => T): FoldSelectorBuilder[T] =
        macro SelectorBuilder.timeoutImpl[T,FoldSelectorBuilder[T]]

   @inline
   def timeoutWithFlowTerminationAsync(t:FiniteDuration,
                       f: (ExecutionContext, FlowTermination[T], FiniteDuration) => Future[T] ): this.type =
        withTimeout(t){ sk => Some(f(ec,sk.flowTermination,t) map Function.const(sk)  ) }
                              

  def idle(body:T): FoldSelectorBuilder[T] =
         macro SelectorBuilder.idleImpl[T,FoldSelectorBuilder[T]]


}

/**
 * Short name for use in fold signature 
 **/
class FoldSelect[T](sf:SelectFactory) extends FoldSelectorBuilder[T]
{
   override def api = sf.api
}

class FoldSelectorBuilderImpl(val c:Context)
{
  import c.universe._

   /**
    *```
    * selector.afold(s0) { (s, selector) =>
    *    selector.match {
    *      case x1: in1.read => f1
    *      case x2: in2.read => f2
    *      case x3: out3.write if (x3==v) => f3
    *      case _  => f4
    *    }
    * }
    *```
    * will be transformed to
    *{{{
    * var s = s0
    * val bn = new FoldSelector
    * bn.reading(in1)(x1 => f1 map {s=_; s; writeBarrier})
    * bn.reading(in2)(x2 => f2 map {s=_; s; writeBarrier})
    * bn.writing(out3,v)(x2 => f2 map {s=_; s})
    * bn.idle(f4 map {s=_; s})
    *}}}
    *
    * also supported partial function syntax:
    *
    *{{{
    * selector.afold((0,1)){ 
    *    case ((x,y),s) => s match {
    *      case x1: in1.read => f1
    *      case x2: in2.read => f2
    *      case x3: out3.write if (x3==v) => f3
    *      case _  => f4
    *    }
    *}}}
    * will be transformed to:
    *{{{
    * var s = s0
    * val bn = new FoldSelector
    * bn.reading(in1)(x1 => async{ val x = s._1;
    *                              val y = s._2;
    *                              s = f1; writeBarrier; s} })
    * bn.reading(in2)(x2 => { val x = s._1;
    *                         val y = s._2;
    *                         s=f2; s} })
    * bn.writing(out3,v[x/s._1;y/s._2])
    *                     (x2 => s=f2; s})
    *}}}
    **/
   def afold[S:c.WeakTypeTag](s:c.Expr[S])(op:c.Expr[(S,FoldSelect[S])=>S]):c.Expr[Future[S]] =
   {
    val foldParse = parseFold(op)
    val sName = foldParse.stateVal.name
    val bn = TermName(c.freshName("fold"))
    val ncases = foldParse.selectCases.map(preTransformCaseDef(foldParse,_))
    val tree = Block(
           atPos(s.tree.pos)(q"var $sName = ${s}") ::
           q"val ${bn} = new FoldSelect[${weakTypeOf[S]}](${c.prefix})"::
           transformSelectMatch(bn,ncases),
           q"${bn}.go"
          )
    c.Expr[Future[S]](tree)
   }

   def transformSelectMatch(bn:TermName,cases: List[CaseDef]):List[Tree]=
     SelectorBuilder.transformSelectMatch(c)(bn,cases)

   def fold[S:c.WeakTypeTag](s:c.Expr[S])(op:c.Expr[(S,FoldSelect[S])=>S]):c.Expr[S] =
     c.Expr[S](q"scala.async.Async.await(${afold(s)(op).tree})")


   case class FoldParse(
     stateVal: ValDef,
     projections:  List[c.Symbol],
     selectValName: c.TermName,
     selectCases: List[CaseDef]
   )

   def withProjAssignments(fp:FoldParse, patSymbol: Symbol, body:c.Tree):c.Tree =
   {
     val stateName=fp.stateVal.name
     val projAssignments = (fp.projections.zipWithIndex) map { 
      case (sym,i) => 
        val pf = TermName("_" + (i+1).toString)
        q"val ${sym.name.toTermName} = $stateName.$pf"  
     }
     val nbody = cleanIdents(body,fp.projections.toSet + fp.stateVal.symbol + patSymbol)
     if (projAssignments.isEmpty)
       nbody
     else {
       Block(projAssignments,cleanIdents(nbody,fp.projections.toSet))
     }
   }

   private def cleanIdents(tree:c.Tree,symbols:Set[Symbol]):Tree =
   {
    val tr = new Transformer {
      override def transform(tree:c.Tree):c.Tree =
        tree match {
          case Ident(s) => if (symbols.contains(tree.symbol)) {
                             // create new tree without associated symbol.
                             //(typer wil reassociate one).
                             atPos(tree.pos)(Ident(s))
                           } else {
                             super.transform(tree)
                           }
          case ValDef(m,s,rhs,lhs) => if (symbols.contains(tree.symbol)) {
                             atPos(tree.pos)(ValDef(m,s,rhs,lhs))
                             super.transform(tree)
                           } else {
                             super.transform(tree)
                           }
          case _ => super.transform(tree)
        }
    }
    tr.transform(tree)
   }

   def substProj(fp:FoldParse, body:c.Tree):c.Tree =
        substProj(fp.projections, fp.stateVal.symbol, body)

   def substProj(projections: List[c.Symbol], stateSymbol: c.Symbol, body:c.Tree):c.Tree =
   {
     val pi = projections.zipWithIndex.toMap
     val sName = stateSymbol.name.toTermName
     val transformer = new Transformer() {
       override def transform(tree:Tree):Tree =
         tree match {
           case Ident(name) => pi.get(tree.symbol) match {
                                  case Some(n) => val proj = TermName("_"+(n+1).toString)
                                                  atPos(tree.pos)(q"${sName}.${proj}")
                                  case None => 
                                       if (tree.symbol eq stateSymbol) {
                                         atPos(tree.pos)(Ident(sName))
                                       } else {
                                         super.transform(tree)
                                       }
                               }
           case _ => super.transform(tree)
         }
     }
     return transformer.transform(body)
   } 

   def preTransformCaseDefBody(fp:FoldParse, patSymbol: Symbol, body:c.Tree):c.Tree =
   {
     val sName = fp.stateVal.name
     val tmpName = TermName(c.freshName("tmp"))
     q"""
         val $tmpName = ${withProjAssignments(fp,patSymbol,body)}
         $sName = $tmpName
         $sName
     """
   }

   def preTransformCaseDef(fp:FoldParse,cd:CaseDef):CaseDef =
   {
     val patSymbol = cd.pat.symbol
     if (cd.guard.isEmpty) {
         val (pat, guard) = cd.pat match {
                     case Bind(name,t) => 
                       fp.projections.indexWhere(_.name == name) match {
                          case -1 => (cd.pat, cd.guard)
                          case idx => 
                            // TODO: move parsing of rw-select to ASTUtil and
                            // eliminate code duplication with SelectorBuilder
                            t match {
                              case Typed(_,tp:TypeTree) =>
                                  val tpoa = if (tp.original.isEmpty) tp else tp.original
                                  val tpo = MacroUtil.skipAnnotation(c)(tpoa)
                                  tpo match {
                                     case Select(ch,TypeName("read")) =>
                                               //TODO (low priority): implement shadowing instead abort
                                               c.abort(cd.pat.pos,"Symbol in pattern shadow symbol in state")
                                     case Select(ch,TypeName("write")) =>
                                               val newName = TermName(c.freshName("wrt"))
                                               val newPat = atPos(cd.pat.pos)(Bind(newName,t))
                                               if (!cd.guard.isEmpty) {
                                                  c.abort(cd.pos,"guard must be empty");
                                               }
                                               val sName = fp.stateVal.name.toTermName
                                               val proj = TermName("_"+(idx+1))
                                               val newGuard = q"${newName} == $sName.$proj" 
                                               (newPat,newGuard)
                                     case _ => 
                                               //TODO: implement read/write syntax
                                               c.abort(cd.pat.pos,"read/write is required we have "+
                                                                         MacroUtil.shortString(c)(t))
                                  }
                               case _ =>
                                     c.abort(cd.pat.pos,"x:channel.read or x:channel.write form is required")
                            }
                       }
                     case Ident(TermName("_")) => (cd.pat, cd.guard)
                     case _ => c.abort(cd.pat.pos,"expected Bind or Default in pattern, have:"+cd.pat)
                   }
       atPos(cd.pos)(CaseDef(pat,substProj(fp,guard),preTransformCaseDefBody(fp,patSymbol,cd.body)))
     }else{
       atPos(cd.pos)(CaseDef(cd.pat,substProj(fp,cd.guard),preTransformCaseDefBody(fp,patSymbol,cd.body)))
     }
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
                                      FoldParse(
                                         stateVal = x,
                                         projections = params map { _.symbol },
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
                              FoldParse(
                                stateVal = x, projections = List(),
                                selectValName = selectorName.toTermName, 
                                selectCases = cases
                              )
                            } else {
                              c.abort(op.tree.pos,"expected choice over selector in fold")
                            }
                         }
                       // TODO: check that 'choice' == 'y'
       case Function(params,something) =>
               c.abort(op.tree.pos,"match is expected in select.fold, we have: "+MacroUtil.shortString(c)(op.tree));
       case _ =>
               c.abort(op.tree.pos,"inline function is expected in select.fold, we have: "+MacroUtil.shortString(c)(op.tree));
    }
   }


}

object FoldSelectorBuilder
{




}


