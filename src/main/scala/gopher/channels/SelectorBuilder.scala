package gopher.channels

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import gopher._
import scala.concurrent._
import scala.annotation.unchecked._

class SelectorBuilder[A](api: GopherAPI)
{


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
   def withReader[B](ch:Input[B], f: (B,ContRead[B,A]) => Option[Future[Continuated[A]]]): this.type =
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

   implicit def ec: ExecutionContext = api.executionContext

   val selector=new Selector[A](api)

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
            buildAsyncCall[T,S](c)(valdefs,body,
                   { (nvaldefs, nbody) =>
                     q"""${c.prefix}.writingWithFlowTerminationAsync(${ch},${x},
                             ${Function(nvaldefs,nbody)}
                       )
                     """
                   })
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

}

class ForeverSelectorBuilder(api: GopherAPI) extends SelectorBuilder[Unit](api)
{

         
   def reading[A](ch: Input[A])(f: A=>Unit): ForeverSelectorBuilder =
        macro SelectorBuilder.readingImpl[A,Unit,ForeverSelectorBuilder] 
                    // internal error in compiler when using this.type as S
      

   @inline
   def readingWithFlowTerminationAsync[A](ch: Input[A], f: (ExecutionContext, FlowTermination[Unit], A) => Future[Unit] ): this.type =
      withReader[A]( ch, (e, cr) => Some(f(ec,cr.flowTermination,e) map Function.const(cr)) )

   def writing[A](ch: Output[A], x: A)(f: A => Unit): ForeverSelectorBuilder = 
        macro SelectorBuilder.writingImpl[A,Unit,ForeverSelectorBuilder]

   @inline
   def writingWithFlowTerminationAsync[A](ch:Output[A], x: =>A, f: (ExecutionContext, FlowTermination[Unit], A) => Future[Unit] ): ForeverSelectorBuilder =
       withWriter[A](ch,   { cw => Some(x,f(ec,cw.flowTermination, x) map Function.const(cw)) } )


   def idle(body:Unit): ForeverSelectorBuilder =
         macro SelectorBuilder.idleImpl[Unit,ForeverSelectorBuilder]
    
   @inline
   def idleWithFlowTerminationAsync(f: (ExecutionContext, FlowTermination[Unit]) => Future[Unit] ): ForeverSelectorBuilder =
      withIdle{ st => Some(f(ec,st.flowTermination) map Function.const(st)) }

    
   def foreach(f:Any=>Unit):Unit = 
        macro ForeverSelectorBuilder.foreachImpl

}

object ForeverSelectorBuilder
{

   def foreachImpl(c:Context)(f:c.Expr[Any=>Unit]):c.Expr[Unit] =
   {
     import c.universe._
     val builder = f.tree match {
       case Function(forvals,Match(choice,cases)) =>
                                foreachTransformMatch(c)(forvals,choice,cases, Nil)
       case Function(forvals,Block(commonExpr,Match(choice,cases))) =>  
                                foreachTransformMatch(c)(forvals,choice,cases, commonExpr)
       case Function(a,b) =>
                     c.abort(f.tree.pos, "match expected in gopher select loop");
       case _ => {
            // TODO: write hlepr functio which wirite first 255 chars of x raw representation
            System.err.println("raw f:"+c.universe.showRaw(f.tree));
            c.abort(f.tree.pos, "match expected in gopher select loop");
       }
    }
    val r = c.Expr[Unit](c.untypecheck(q"scala.async.Async.await(${builder}.go)"))
    System.err.println("foreach: r="+r)
    r
   }

   def foreachTransformMatch(c:Context)(forvals:List[c.universe.ValDef],
                                        choice:c.Tree,
                                        cases:List[c.universe.CaseDef],
                                        commonExpr:List[c.Tree]):c.Tree =
   {
     import c.universe._
     Console.println("forvals="+forvals)
     Console.println("choice="+choice)
     Console.println("cases="+cases)
     Console.println("commonExpr="+commonExpr)
     // TODO: check that choice and forvals are the same.
     val bn = TermName(c.freshName)
     val calls = cases map { cs =>
        cs.pat match {
           case Bind(ident, t)
                      => foreachTransformReadWriteCaseDef(c)(bn,cs)
           case Ident(TermName("_")) => foreachTransformIdleCaseDef(c)(bn,cs)
           case _ => c.abort(cs.pat.pos,"expected Bind or Default in pattern, have:"+showRaw(cs.pat))
        }
     }
     Block( 
       (q"val ${bn} = ${c.prefix}" :: calls) : _*
     )
   }

   def foreachTransformReadWriteCaseDef(c:Context)(builderName:c.TermName, caseDef: c.universe.CaseDef):c.Tree=
   {
    import c.universe._
    Console.println("rw: caseDef.pat"+caseDef.pat)
    Console.println("rw: caseDef.guard"+caseDef.guard)
    Console.println("rw: caseDef.body"+caseDef.body)
    caseDef.pat match {
      case Bind(name,Typed(_,tp:c.universe.TypeTree)) =>
                    val tpo = if (tp.original.isEmpty) tp else tp.original
                    tpo match {
                       case Select(ch,TypeName("read")) =>
                                   if (!caseDef.guard.isEmpty) {
                                     c.abort(caseDef.guard.pos,"guard is not supported in select case")
                                   }
                                   val termName = /* name.toTermName */ TermName("ir")
                                   val bodyTransformer = new Transformer {
                                                             override def transform(tree:Tree): Tree =
                                                                tree match {
                                                                   case Ident(`termName`) =>
                                                                                       Ident(termName)
                                                                   case _ =>
                                                                            super.transform(tree)
                                                                }
                                                         }
                                   val body = bodyTransformer.transform(caseDef.body)
                                   val readParam = ValDef(Modifiers(Flag.PARAM),termName,TypeTree(),EmptyTree)
                                   val reading = q"${builderName}.reading(${ch}){ ${readParam} => ${body} }"
                   
                                   System.err.println("raw caseDef:"+showRaw(caseDef.body))
                                   System.err.println(s"read from ${ch}")
                                   System.err.println(s"r:"+reading)
                                   reading
                       case Select(ch,TypeName("write")) =>
                                   System.err.println(s"write to ${ch}")
                                   val termName = name.toTermName
                                   val expression = if (!caseDef.guard.isEmpty) {
                                                      parseGuardInSelectorCaseDef(c)(termName,caseDef.guard)
                                                    } else {
                                                      Ident(termName)
                                                    }
                                   val param = ValDef(Modifiers(Flag.PARAM),termName,TypeTree(),EmptyTree)
                                   val writing = q"${builderName}.writing(${ch},${expression})(${param} => ${caseDef.body} )"
                                   System.err.println("r:"+writing)
                                   writing
                       case _ =>
                         c.abort(tp.pos, "match must be in form x:channel.write or x:channel.read");
                    }
      case Bind(name,x) =>
                    x match {
                      case Typed(_,tp) =>
                            System.err.println("TP:"+showRaw(tp))
                      case _ =>
                    }
                    c.abort(caseDef.pat.pos, "match must be in form x:channel.write or x:channel.read");
      case _ =>
            c.abort(caseDef.pat.pos, "match must be in form x:channel.write or x:channel.read");
    }

   }

   def parseGuardInSelectorCaseDef(c: Context)(name: c.TermName, guard:c.Tree): c.Tree =
   {
     import c.universe._
     Console.println(showRaw(guard))
     guard match {
        case Apply(Select(Ident(`name`),TermName("$eq$eq")),List(expression)) =>
               expression
        case _ =>
               c.abort(guard.pos, s"expected ${name}==<expression> in select write guard")
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


class OnceSelectorBuilder[T](api: GopherAPI) extends SelectorBuilder[T@uncheckedVariance](api)
{

   def reading[A](ch: Input[A])(f: A=>T): OnceSelectorBuilder[T] =
        macro SelectorBuilder.readingImpl[A,T,OnceSelectorBuilder[T]] 

   @inline
   def readingWithFlowTerminationAsync[A](ch: Input[A], f: (ExecutionContext, FlowTermination[T], A) => Future[T] ): OnceSelectorBuilder[T] =
       withReader[A](ch,  { (e, cr) => Some(f(ec,cr.flowTermination,e) map ( Done(_,cr.flowTermination))) } )

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

}


class SelectFactory(api: GopherAPI)
{
 
  /**
   * forever builder. 
   *@Seee ForeverSelectorBuilder
   */
  def forever: ForeverSelectorBuilder = new ForeverSelectorBuilder(api)

  def once[T]: OnceSelectorBuilder[T] = new OnceSelectorBuilder[T](api)

  /**
   * generic selector builder
   */
  def loop[A]: SelectorBuilder[A] = new SelectorBuilder[A](api)
}

