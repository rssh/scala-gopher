package gopher.channels

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
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

    // Loook's like bug in 'untypecheck' : when we split cassDef on few functions, than sometines, symbols
    // entries in identifier tree are not cleared.  
    def clearIdent(name:c.Name, tree:Tree):Tree =
    {
      val termName = name.toTermName
      val transformer = new Transformer {
            override def transform(tree:Tree): Tree =
                tree match  {
                   case Ident(`termName`) => Ident(termName)
                   case _                 => super.transform(tree)
                }
      }
      transformer.transform(tree)
    }

    caseDef.pat match {
      case Bind(name,Typed(_,tp:c.universe.TypeTree)) =>
                    val tpo = if (tp.original.isEmpty) tp else tp.original
                    val termName = name.toTermName 
                    val param = ValDef(Modifiers(Flag.PARAM),termName,TypeTree(),EmptyTree)
                    val body = clearIdent(name,caseDef.body)
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
                    c.abort(caseDef.pat.pos, "match must be in form x:channel.write or x:channel.read, have: ${rawToShow}");
      case _ =>
            c.abort(caseDef.pat.pos, "match must be in form x:channel.write or x:channel.read");
    }

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
 * Builder for 'forever' selector. Can be obtained as `gopherApi.select.forever`.
 **/
trait ForeverSelectorBuilder extends SelectorBuilder[Unit]
{

         
   def reading[A](ch: Input[A])(f: A=>Unit): ForeverSelectorBuilder =
        macro SelectorBuilder.readingImpl[A,Unit,ForeverSelectorBuilder] 
                    // internal error in compiler when using this.type as S
      

   def readingWithFlowTerminationAsync[A](ch: Input[A], f: (ExecutionContext, FlowTermination[Unit], A) => Future[Unit] ): this.type =
   {
     lazy val cont = ContRead(normalized, ch, selector)
     def normalized(_cont:ContRead[A,Unit]):Option[ContRead.In[A]=>Future[Continuated[Unit]]] = 
                                    Some(ContRead.liftIn(_cont)(a=>f(ec,selector,a) map Function.const(cont))) 
     withReader[A](ch, normalized) 
   }

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

   /**
    * provide syntax for running select loop inside go (or async) block
    * example of usage:
    *
    *{{{
    *  go {
    *    .....
    *    for(s <- gopherApi.select.forever) 
    *      s match {
    *        case x: ch1.read => do something with x
    *        case q: chq.read => implicitly[FlowTermination[Unit]].doExit(())
    *        case y: ch2.write if (y=expr) => do something with y
    *        case _ => do somethig when idle.
    *      }
    *}}}
    *
    * Note, that you can use implicit instance of [FlowTermination[Unit]] to stop loop.
    **/
   def foreach(f:Any=>Unit):Unit = 
        macro SelectorBuilder.foreachImpl[Unit]

   /**
    * provide syntax for running select loop as async operation.
    *
    *{{{
    *  val receiver = gopherApi.select.forever{
    *                   case x: channel.read => Console.println(s"received:\$x")
    *                 }
    *}}}
    */
   def apply(f: PartialFunction[Any,Unit]): Future[Unit] =
        macro SelectorBuilder.applyImpl[Unit]

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
}

