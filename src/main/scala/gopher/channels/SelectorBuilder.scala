package gopher.channels

import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context
import scala.reflect.api._
import gopher._
import gopher.util._
import scala.concurrent._
import scala.annotation.unchecked._

trait SelectorBuilder[A]
{

   def api: GopherAPI

   def onRead[E](ch:Input[E])(arg: ReadSelectorArgument[E,A]): this.type =
   {
     selector.addReader(ch,arg.normalizedFun)
     this
   }

   def onWrite[E](ch:Output[E])(arg: WriteSelectorArgument[E,A]): this.type =
   {
     selector.addWriter(ch,arg.normalizedFun)
     this
   }

   def onIdle(arg: SkipSelectorArgument[A]): this.type =
   {
     selector.addIdleSkip(arg.normalizedFun)
     this
   }

   @inline
   def withReader[B](ch:Input[B], f: ContRead[B,A] => Option[ContRead.In[B]=>Future[Continuated[A]]]): this.type =
   {
     selector.addReader(ch,f)
     this
   }

   @inline
   def withWriter[B](ch:Output[B], f: ContWrite[B,A] => Option[(B,Future[Continuated[A]])] ): this.type =
   {
     selector.addWriter(ch,f)
     this
   } 

   @inline
   def withIdle(f: Skip[A] => Option[Future[Continuated[A]]]):this.type =
   {
     selector.addIdleSkip(f)
     this
   }
     

   def go: Future[A] = selector.run

   // for call from SelectorTransforment wich have another 'go'
   def selectorRun: Future[A] = selector.run

   implicit def ec: ExecutionContext = api.executionContext

   private[gopher] var selector=new Selector[A](api)

   // used for reading from future
   @inline
   def futureInput[A](f:Future[A]):FutureInput[A]=api.futureInput(f)

}

object SelectorBuilder
{

   def readingImpl[A,B:c.WeakTypeTag,S](c:Context)(ch:c.Expr[Input[A]])(f:c.Expr[A=>B]):c.Expr[S] =
   {
      import c.universe._
      f.tree match {
         case Function(valdefs, body) => 
               buildAsyncCall[B,S](c)(valdefs,body, 
                                { (nvaldefs, nbody) =>
                                 q"""${c.prefix}.readingWithFlowTerminationAsync(${ch},
                                       ${Function(nvaldefs,nbody)}
                                      )
                                  """
                                })
         case _ => c.abort(c.enclosingPosition,"argument of reading.apply must be function")
      }
   }

   def writingImpl[A,T:c.WeakTypeTag,S](c:Context)(ch:c.Expr[Output[A]],x:c.Expr[A])(f:c.Expr[A=>T]):c.Expr[S] =
   {
     import c.universe._
     f.tree match {
         case Function(valdefs, body) => 
            val retval = buildAsyncCall[T,S](c)(valdefs,body,
                   { (nvaldefs, nbody) =>
                     q"""${c.prefix}.writingWithFlowTerminationAsync(${ch},${x},
                             ${Function(nvaldefs,nbody)}
                       )
                     """
                   })
            retval
         case _ => c.abort(c.enclosingPosition,"second argument of writing must have shape Function(x,y)")
     }
   }

   def transformDelayedMacroses[T:c.WeakTypeTag](c:Context)(block:c.Tree):c.Tree =
   {
     import c.universe._
     val transformer = new Transformer {
        override def transform(tree:Tree): Tree =
          tree match {
             case Apply(TypeApply(Select(obj,TermName("implicitly")),List(objType)), args) =>
                    // unresolve implicit references of specific type
                    if (obj.tpe =:= typeOf[Predef.type] &&
                        objType.tpe <:< typeOf[FlowTermination[Nothing]]
                        ) {
                       TypeApply(Select(obj,TermName("implicitly")),List(objType))
                    } else {
                       super.transform(tree)
                    }
             case Apply(TypeApply(Select(obj,member),objType), args) =>
                    if (obj.tpe =:= typeOf[CurrentFlowTermination.type] ) {
                       member match {
                          case TermName("exit") => 
                                 Apply(TypeApply(Select(obj,TermName("exitDelayed")),objType), args) 
                          case _ => super.transform(tree)
                       }
                    } else {
                       super.transform(tree)
                    }
             case Apply(Select(obj,member), args) =>
                    if (obj.tpe =:= typeOf[CurrentFlowTermination.type] ) {
                       member match {
                          case TermName("exit") => 
                                   Apply(Select(obj,TermName("exitDelayed")),args)
                          case _ => super.transform(tree)
                       }
                    } else {
                       super.transform(tree)
                    }
             case _ => 
                    super.transform(tree)
          }
     }
     transformer.transform(block)
   }

   def buildAsyncCall[T:c.WeakTypeTag,S](c:Context)(valdefs: List[c.universe.ValDef], body: c.Tree,
                                     lastFun: (List[c.universe.ValDef], c.Tree) => c.Tree): c.Expr[S] =
   {
     import c.universe._
     val Seq(ft, ft1, ec, ec1) = Seq("ft","ft","ec","ec1") map (x => TermName(c.freshName(x)))
     val ftParam = ValDef(Modifiers(Flag.PARAM),ft,tq"gopher.FlowTermination[${weakTypeOf[T]}]",EmptyTree)
     val ecParam = ValDef(Modifiers(Flag.PARAM),ec,tq"scala.concurrent.ExecutionContext",EmptyTree)
     val nvaldefs = ecParam::ftParam::valdefs
     val nbody = q"""{
                      implicit val ${ft1} = ${ft}
                      implicit val ${ec1} = ${ec}
                      scala.async.Async.async(${transformDelayedMacroses[T](c)(body)})(${ec})
                     }
                  """
     val newTree = lastFun(nvaldefs,nbody)
     // untypecheck is necessory: otherwise exception in async internals
     c.Expr[S](c.untypecheck(newTree))
   }

   def idleImpl[T:c.WeakTypeTag,S](c:Context)(body:c.Expr[T]):c.Expr[S] =
   {
     import c.universe._
     SelectorBuilder.buildAsyncCall[T,S](c)(Nil,body.tree,
                   { (nvaldefs, nbody) =>
                      q"""${c.prefix}.idleWithFlowTerminationAsync(
                                    ${Function(nvaldefs,nbody)}
                          )
                       """
                   })
   }

   def foreachImpl[T](c:Context)(f:c.Expr[Any=>T]):c.Expr[T] =
   {
     import c.universe._
     val builder = f.tree match {
       case Function(forvals,Match(choice,cases)) =>
                                // TOD: check that forvals and choice are same 
                                foreachTransformMatch(c)(cases)
       // TODO: think, are we need syntax with common-expr ?
       //case Function(forvals,Block(commonExpr,Match(choice,cases))) =>  
       //                         foreachTransformMatch(c)(forvals,choice,cases, commonExpr)
       case Function(a,b) =>
                     c.abort(f.tree.pos, "match expected in gopher select loop, have: ${MacroUtil.shortString(b)} ");
       case _ => {
            c.abort(f.tree.pos, "match expected in gopher select loop, have: ${MacroUtil.shortString(f.tree)}");
       }
    }
    c.Expr[T](c.untypecheck(q"scala.async.Async.await(${builder}.go)"))
   }

   def builderImpl[T](c:Context)(f:c.Expr[PartialFunction[Any,T]]):c.Tree =
   {
     import c.universe._
     f.tree match {
        case q"{case ..$cases}" =>
                  foreachTransformMatch(c)(cases)
        case _ => c.abort(f.tree.pos,"expected partial function with syntax case ... =>, have ${MacroUtil.shortString(f.tree)}");
     }
   }

   def applyImpl[T](c:Context)(f:c.Expr[PartialFunction[Any,T]]):c.Expr[Future[T]] =
   {
     import c.universe._
     val builder = builderImpl[T](c)(f)
     c.Expr[Future[T]](c.untypecheck(q"${builder}.go"))
   }

   /**
    * processor: loop => just add waiters to this selector.
    */
   def loopImpl[T](c:Context)(f:c.Expr[PartialFunction[Any,T]]):c.Expr[Unit] =
   {
     import c.universe._
     val builder = builderImpl[T](c)(f)
     c.Expr[Unit](c.untypecheck(q"{selectorInit = ()=>${builder}; selectorInit()}"))
   }

   def foreachTransformMatch(c:Context)(cases:List[c.universe.CaseDef]):c.Tree =
   {
     import c.universe._
     val bn = TermName(c.freshName)
     val calls = cases map { cs =>
        cs.pat match {
           case Bind(ident, t)
                      => foreachTransformReadWriteCaseDef(c)(bn,cs)
           case Ident(TermName("_")) => foreachTransformIdleCaseDef(c)(bn,cs)
           case _ => c.abort(cs.pat.pos,"expected Bind or Default in pattern, have:"+cs.pat)
        }
     }
     q"""..${q"val ${bn} = ${c.prefix}" :: calls}"""
   }

   def foreachTransformReadWriteCaseDef(c:Context)(builderName:c.TermName, caseDef: c.universe.CaseDef):c.Tree=
   {
    import c.universe._

    val symbolsToErase = Set(caseDef.pat.symbol, caseDef.pat.symbol.owner)

    // Loook's like bug in 'untypecheck' : when we split cassDef on few functions, than sometines, symbols
    // entries in identifier tree are not cleared.  
    //   So, we 'reset' symbols which belong to caseDef which will be erased by macros
    //   //TODO: check, may be will be better to use scala-compiler internal API and changeOwner instead.
    def clearCaseDefOwner(oldName:c.Name, newName: c.TermName, tree:Tree):Tree =
    {
      val oldTermName = oldName.toTermName

      def changeName(name: c.TermName):c.TermName =
        if (name==oldTermName) newName else name

      def ownerWillBeErased(sym:Symbol):Boolean =
           symbolsToErase.contains(sym)

      class ClearTransformer extends Transformer {

            var insideMustBeErased: Boolean = false

            override def transform(tree:Tree): Tree =
            {
              tree match {
               case Typed(ident@Ident(`oldTermName`),_) => if (ident.symbol!=null && ownerWillBeErased(ident.symbol))   
                                                               Ident(newName)
                                                           else
                                                               super.transform(tree)
               case _ =>
                if (tree.symbol != null) {
                    if (ownerWillBeErased(tree.symbol)) {
                          var prevMustBeErased = insideMustBeErased
                          insideMustBeErased = true
                          val rtree = doClear(tree)
                          insideMustBeErased = prevMustBeErased
                          rtree
                    } else super.transform(tree)
                } else {
                   if (false && insideMustBeErased) {
                       doClear(tree)
                   } else 
                       super.transform(tree)
                }
              }
            }

            def doClear(tree: c.Tree):c.Tree =
            {
              tree match {
                  case Ident(name:TermName) => Ident(changeName(name)) // TODO: setPos
                  case Bind(name:TermName,body) => 
                                    Bind(changeName(name),transform(body)) // TODO: setPos
                  case ValDef(mods,name,tpt,rhs) => 
                                    ValDef(mods,changeName(name),transform(tpt),transform(rhs))
                  case _   => 
                      c.abort(tree.pos,"""Unexpected shape for tree with caseDef owner, which erased by macro,
                                         please, fire bug-report to scala-gopher, raw="""+showRaw(tree))
              }
            }

      }
      val transformer = new ClearTransformer()
      transformer.transform(tree)
    }

    def skipAnnotation(x: Tree):Tree =
     x match {
        case Annotated(_,arg) => arg
        case _ => x
     }

    def unUnapplyPattern(x:Tree):Tree =
      x match {
         case Bind(name, UnApply(fun,List(t@Typed(_,_))) ) => Bind(name,t)
         case _ => x
      }

    val retval = unUnapplyPattern(caseDef.pat) match {
      case Bind(name,Typed(_,tp:c.universe.TypeTree)) =>
                    val termName = name.toTermName 
                    // when debug problems on later compilation steps, you can create freshName and see visually:
                    // is oldName steel leaked to later compilation phases.
                    val newName = c.freshName(termName)
                    //val newName = termName
                    val tpoa = clearCaseDefOwner(name, newName, if (tp.original.isEmpty) tp else tp.original)
                    val tpo = skipAnnotation( tpoa )
                    val param = ValDef(Modifiers(Flag.PARAM), newName, tpoa ,EmptyTree)
                    val body = clearCaseDefOwner(name,newName,caseDef.body)
                    tpo match {
                       case Select(ch,TypeName("read")) =>
                                   if (!caseDef.guard.isEmpty) {
                                     c.abort(caseDef.guard.pos,"guard is not supported in select case")
                                   }
                                   val reading = q"${builderName}.reading(${ch}){ ${param} => ${body} }"
                                   reading
                       case Select(ch,TypeName("write")) =>
                                   val expression = if (!caseDef.guard.isEmpty) {
                                                      parseGuardInSelectorCaseDef(c)(termName,caseDef.guard)
                                                    } else {
                                                      Ident(termName)
                                                    }
                                   val writing = q"${builderName}.writing(${ch},${expression})(${param} => ${body} )"
                                   writing
                       case _ =>
                         if (caseDef.guard.isEmpty) {
                            c.abort(tp.pos, "row caseDef:"+showRaw(caseDef) );
                            c.abort(tp.pos, "match pattern in select without guard must be in form x:channel.write or x:channel.read");
                         } else {
                            parseGuardInSelectorCaseDef(c)(termName, caseDef.guard) match {
                               case q"scala.async.Async.await[${t}](${readed}.aread):${t1}" =>
                                        // here is 'reverse' of out read macros
                                        val channel = readed match {
                                           case q"gopher.`package`.FutureWithRead[${t2}](${future})" =>
                                                q"${builderName}.futureInput(${future})"
                                           case _ =>
                                                if (readed.tpe <:< typeOf[gopher.channels.Input[_]]) {
                                                   readed
                                                } else if (readed.tpe <:< typeOf[gopher.`package`.FutureWithRead[_]]) {
                                                  q"${builderName}.futureInput(${readed}.aread)"
                                                } else {
                                                   c.abort(readed.pos,"reading in select pattern guide must be channel or future, we have:"+readed.tpe)
                                                }
                                        }
                                        q"${builderName}.reading(${channel})(${param} => ${body} )"
                               case q"scala.async.Async.await[${t}](${ch}.awrite($expression)):${t1}" =>
                                        q"${builderName}.writing(${ch},${expression})(${param} => ${body} )"
                               case x@_ =>
                                  c.abort(tp.pos, "can't parse match guard: "+x);
                            }
                          
                         }
                    }
      case Bind(name,x) =>
                    val rawToShow = x match {
                      case Typed(_,tp) =>
                                     MacroUtil.shortString(c)(tp)
                      case _ =>
                                     MacroUtil.shortString(c)(x)
                    }
                    c.abort(caseDef.pat.pos, s"match must be in form x:channel.write or x:channel.read, have: ${rawToShow}");
      case _ =>
            c.abort(caseDef.pat.pos, "match must be in form x:channel.write or x:channel.read");
    }
    
    retval

   }

   def parseGuardInSelectorCaseDef(c: Context)(name: c.TermName, guard:c.Tree): c.Tree =
   {
     import c.universe._
     guard match {
        case Apply(Select(Ident(`name`),TermName("$eq$eq")),List(expression)) =>
               expression
        case _ =>
               c.abort(guard.pos, s"expected ${name}==<expression> in select guard")
     }
   }

   def foreachTransformIdleCaseDef(c:Context)(builderName:c.TermName, caseDef: c.universe.CaseDef):c.Tree=
   {
    import c.universe._
    if (!caseDef.guard.isEmpty) {
      c.abort(caseDef.guard.pos,"guard is not supported in select case")
    }
    q"${builderName}.idle(${caseDef.body})"
   }

}



/**
 * Builder for 'once' selector. Can be obtained as `gopherApi.select.once`.
 */
trait OnceSelectorBuilder[T] extends SelectorBuilder[T@uncheckedVariance]
{

   def reading[A](ch: Input[A])(f: A=>T): OnceSelectorBuilder[T] =
        macro SelectorBuilder.readingImpl[A,T,OnceSelectorBuilder[T]] 

   @inline
   def readingWithFlowTerminationAsync[A](ch: Input[A], f: (ExecutionContext, FlowTermination[T], A) => Future[T] ): OnceSelectorBuilder[T] =
       withReader[A](ch,  { cr => Some(ContRead.liftIn(cr)(a => 
                                          f(ec,cr.flowTermination,a) map ( Done(_,cr.flowTermination)) 
                                      )                   ) 
                          } )

   /**
    * write x to channel if possible
    */
   def writing[A](ch: Output[A], x: A)(f: A=>T): OnceSelectorBuilder[T] = 
        macro SelectorBuilder.writingImpl[A,T,OnceSelectorBuilder[T]]
 
   @inline
   def writingWithFlowTerminationAsync[A](ch:Output[A], x: =>A, f: (ExecutionContext, FlowTermination[T], A) => Future[T] ): this.type =
        withWriter[A](ch, { cw => Some(x,f(ec,cw.flowTermination,x) map(x => Done(x,cw.flowTermination)) ) } )

   def idle(body: T): OnceSelectorBuilder[T] = 
        macro SelectorBuilder.idleImpl[T,OnceSelectorBuilder[T]]

   @inline
   def idleWithFlowTerminationAsync(f: (ExecutionContext, FlowTermination[T]) => Future[T] ): this.type =
       withIdle{ sk => Some(f(ec,sk.flowTermination) map(x => Done(x,sk.flowTermination)) ) }

   def foreach(f:Any=>T):T = 
        macro SelectorBuilder.foreachImpl[T]

   def apply(f: PartialFunction[Any,T]): Future[T] =
        macro SelectorBuilder.applyImpl[T]

}

trait FoldSelectorBuilder[T] extends SelectorBuilder[T]
{

   def readingWithFlowTerminationAsync[A](ch: Input[A], f: (ExecutionContext, FlowTermination[T], A) => Future[T] ): this.type =
   {
     def normalized(_cont: ContRead[A,T]):Option[ContRead.In[A]=>Future[Continuated[T]]] =
            Some(ContRead.liftIn(_cont){ a=>
                    f(ec,selector,a) map Function.const(ContRead(normalized,ch,selector))
                })
     withReader[A](ch, normalized) 
   }


}

/**
 * Short name for use in fold signature 
 **/
trait FoldSelect[T] extends FoldSelectorBuilder[T]
{
   //override def api = theApi
}

object FoldSelectorBuilder
{

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
    *                              s = f1; writeBarrier} })
    * bn.reading(in2)(x2 => { val x = s._1;
    *                         val y = s._2;
    *                         f2 map {s=_; s; writeBarrier} })
    * bn.writing(out3,{val x1=s._1
    *                  val x2=s._2
    *                  v})(x2 => f2 map {s=_; s})
    *}}}
    **/
   def foldImpl[S](c:Context)(s:c.Expr[S])(op:c.Expr[(S,FoldSelect[S])=>S]):c.Expr[S] =
   {
    import c.universe._
    val (stateVar,selectVar,cases) = parseFold(c)(op)
    System.err.println("stateVar:"+stateVar)
    System.err.println("selectVar:"+selectVar)
    System.err.println("cases:")
    for(c <- cases){
      System.err.println(c)
      System.err.println(showRaw(c))
    }
    val bn = c.freshName("fold")
    val tree = q"""
          { 
           val ${bn} = new FoldSelect(${c.prefix})
           ${bn}.transformFold(op)
          }
    """
    c.Expr[S](tree)
   }

   def parseFold[S](c:Context)(op: c.Expr[(S,FoldSelect[S])=>S]): (c.Tree, c.Tree, List[c.Tree]) = 
   {
    import c.universe._
    op.tree match {
       case Function(List(x,y),Match(choice,cases)) => 
                         System.err.println("choice="+choice+", row:"+showRaw(choice))
                         if (choice.symbol != y.symbol) {
                            System.err.println("QQQ")
                            if (cases.length == 1) {
                                cases.head match {
                                 case CaseDef(Apply(TypeTree(),
                                                    List(Apply(TypeTree(),params),Bind(sel,_))),
                                              guard,
                                              Match(Ident(choice1),cases1)) =>
                                   System.err.println("XXX:params="+params)
                                   System.err.println("XXX:params="+showRaw(params))
                                   System.err.println("XXX:sel="+sel)
                                   System.err.println("XXX:sel="+showRaw(sel))
                                   System.err.println("XXX:choice1="+choice1)
                                   System.err.println("XXX:choice1="+showRaw(choice1))
                                   if (sel == choice1) {
                                      System.err.println("yes!")
                                   } else {
                                   }
                                   (x,y,cases)
                                 case _ =>
                                    c.abort(op.tree.pos,"match agains selector in pf is expected")
                                }
                            } else {
                                c.abort(op.tree.pos,"partial function in fold must have one case")
                            } 
                         } else {
                           (x,y,cases)
                         }
                       // TODO: check that 'choice' == 'y'
       case Function(params,something) =>
               c.abort(op.tree.pos,"match is expected in select.fold, we have: "+MacroUtil.shortString(c)(op.tree));
       case _ =>
               c.abort(op.tree.pos,"inline function is expected in select.fold, we have: "+MacroUtil.shortString(c)(op.tree));
    }
   }

}

/**
 * Factory for select instantiation.
 * Can be obtained via gopherAPI
 *
 * {{{
 *   val selector = gopherApi.select.forever
 *   for(s <- selector) ...
 * }}}
 */
class SelectFactory(api: GopherAPI)
{
 
  selectFactory =>

  trait SelectFactoryApi
  {
    def api = selectFactory.api
  }

  /**
   * forever builder. 
   *@see ForeverSelectorBuilder
   */
  def forever: ForeverSelectorBuilder = new ForeverSelectorBuilder with SelectFactoryApi {}

  /**
   * once builder, where case clause return type is `T`
   */
  def once[T]: OnceSelectorBuilder[T] = new OnceSelectorBuilder[T] with SelectFactoryApi {}

  /**
   * generic selector builder
   */
  def loop[A]: SelectorBuilder[A] = new SelectorBuilder[A] with SelectFactoryApi {}

  
  def fold[S](s:S)(op:(S,Any)=>S):S = macro FoldSelectorBuilder.foldImpl[S]

  def foldWhile[S](s:S)(op:(S,FoldSelect[S])=>(S,Boolean)) = ???

}

