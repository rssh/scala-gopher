package gopher.goasync

import scala.language.experimental.macros
import scala.language.reflectiveCalls
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import gopher._
import gopher.util._
import scala.concurrent._
import scala.annotation.unchecked._


/**
 * async arround go. 
 *
 * Basicly go is 
 *   1. translate await-like exressions inside inline functions to calls of appropriative async functions.
 *      (or show error if not found).
 *```
 *      x.foreach{ x => p; await(x); .. }
 *```
 *  become
 *```
 *      await( transform-defer( x.foreachAsync{ x => async(p; await(x); ..) }) )
 *```
 *    (note, that channel.read macroses are expanded to await-s on this point)
 *
 *   2. transform defer calls if defer statement is found inside go:
 *```
 *   asnyc{ p .. defer(x) ..  }
 *```
 *   become (reallity is a little complext, here is just idea)
 *```
 *   { val d = new Defers(); async{  p .. d.defer(x) ..  }.onComplete(d.tryProcess) }
 *```
 */
object GoAsync
{

 //TODO: add handling of try/catch and operations inside collections.

   def goImpl[T:c.WeakTypeTag](c:Context)(body:c.Expr[T])(ec:c.Expr[ExecutionContext]):c.Expr[Future[T]] =
   {
     import c.universe._
     val nbody = GoAsync.transformAsyncBody[T](c)(body.tree)
     val r = if (containsDefer(c)(body)) {
       val defers = TermName(c.freshName)
       val promise = TermName(c.freshName)
       // asyn transform wantstyped tree on entry, so we must substitute 'defers' to untyped 
       // values after it, no before.
                         q"""
                             gopher.goasync.GoAsync.transformDeferMacro[${c.weakTypeOf[T]}](
                               {implicit val ${defers} = new Defers[${c.weakTypeOf[T]}]()
                                val ${promise} = Promise[${c.weakTypeOf[T]}]()
                                scala.async.Async.async(${nbody})(${ec}).onComplete( x =>
                                     ${promise}.complete(${defers}.tryProcess(x))
                                )(${ec})
                                ${promise}.future
                               }
                             )
                          """
     } else {
       q"scala.async.Async.async(${nbody})(${ec})"
     }
     c.Expr[Future[T]](r)
   }

   def goScopeImpl[T:c.WeakTypeTag](c:Context)(body:c.Expr[T]):c.Expr[T] =
   {
     import c.universe._
     if (containsDefer(c)(body)) {
       val nbody = transformDefer[T](c)(body.tree)
       c.Expr[T](q"""{implicit val defered = new gopher.Defers[${c.weakTypeOf[T]}]()
                      defered.processResult(gopher.Defers.controlTry(${c.untypecheck(nbody)}))
                     }""")
     } else {
       body
     }
   }

   def containsDefer[T:c.WeakTypeTag](c:Context)(body:c.Expr[T]):Boolean = 
   {
    import c.universe._
    val findDefer = new Traverser {
      var found = false
      override def traverse(tree:Tree):Unit =
      {
       if (!found) {
          tree match {
            case q"gopher.`package`.defer(..${args})" => found = true
            case _ => super.traverse(tree)
          }
       } 
      }
    }
    findDefer traverse body.tree
    findDefer.found
   }

   def transformDeferMacro[T](body:Future[T]):Future[T] = macro transformDeferMacroImpl[T]

   def transformDeferMacroImpl[T:c.WeakTypeTag](c:Context)(body:c.Expr[Future[T]]):c.Expr[Future[T]] = 
   {
     c.Expr[Future[T]](c.untypecheck(transformDefer[T](c)(body.tree)))
   }

   def transformDefer[T:c.WeakTypeTag](c:Context)(body:c.Tree):c.Tree = 
   {
    import c.universe._
    val transformer = new Transformer {
      override def transform(tree:Tree):Tree =
       tree match {
            case q"gopher.`package`.defer(..${args})" => 
                       q"implicitly[gopher.Defers[${weakTypeOf[T]}]].defer(..${args map (transform(_))} )"
            case q"$gopher.`package`.recover[$tps](..${args})" =>
                       q"implicitly[gopher.Defers[${weakTypeOf[T]}]].recover(..${args map (transform(_))} )"
            case _ =>
                      super.transform(tree)
       }
    }
    transformer.transform(body)
   }

   def transformAsyncBody[T:c.WeakTypeTag](c:Context)(body:c.Tree):c.Tree = 
   {
    import c.universe._
    var found = false
    val transformer = new Transformer {
      override def transform(tree:Tree):Tree =
       tree match {
         case q"${f1}(${a}=>${b})(..$a2)" =>
           // TODO: cache in tree.
            found = findAwait(c)(b)
            if (found) {
                // this can be implicit parameters of inline apply.
                // whe can distinguish first from second by looking at f1 shape.
                // for now will assume
                System.err.println(s"inline-await + implicit, a2=${a2}")
                System.err.println(s"inline-await ,tree=${tree}")
                System.err.println(s"inline-await ,tree.tpe=${tree.tpe}")
                val isImplicit = f1 match {
                  case TypeApply(Select(x,m),w) =>
                                   System.err.println(s"typed select, x=$x, m=$m, w=$w")
                                   System.err.println(s"x.tpe=${x.tpe}")
                                   System.err.println(s"x.symbol=${x.symbol}")
                                   System.err.println(s"tree.symbol=${tree.symbol}")
                                   if (! (x.tpe eq null) ) {
                                     val sym = x.tpe.member(m) 
                                     System.err.println("sym=$sym")
                                   } else {
                                      true
                                   }
                  case q"$x.$m[$w]" => 
                                   System.err.println(s"typed select, x=$x, m=$m, w=$w")
                  case q"$x.$m" =>
                                   System.err.println(s"select, x=$x, m=$m")
                                      true
                  case q"($x.$m)[$w]" => 
                                   System.err.println(s"typed select-1, x=$x, m=$m, w=$w")
                                      true
                  case _ =>
                                   System.err.println(s"other: ${f1}")
                                   System.err.println(s"raw: ${showRaw(f1)}")
                                      true
                }
                transformInlineHofCall1(c)(f1,a,b,a2) 
            }else{
                super.transform(tree)
            }
         case q"${f1}(${a}=>${b})" =>
            found = findAwait(c)(b)
            if (found) {
                transformInlineHofCall1(c)(f1,a,b,List()) 
            } else {
                super.transform(tree)
            }
         case _ =>
                super.transform(tree)
       }
    }
    val r = transformer.transform(body)
    r
   }
   
   // handle things like:
   //   q"${fun}(${param}=>${body})($implicitParams)" =>
   def transformInlineHofCall1(c:Context)(fun:c.Tree,param:c.Tree,body:c.Tree,implicitParams:List[c.Tree]):c.Tree =
   {
    import c.universe._
    val btype = body.tpe
    // untypechack is necessory, because async-transform later will corrupt
    //  symbols owners inside body
    // [scala-2.11.8]
    val nb = c.untypecheck(body)
    val anb = atPos(body.pos){
       val nnb = transformAsyncBody(c)(nb)
       val ec = c.inferImplicitValue(c.weakTypeOf[ExecutionContext])
       q"(${param})=>scala.async.Async.async[${btype}](${nnb})($ec)"
    }
    val ar = atPos(fun.pos) {
       val uar = if (implicitParams.isEmpty) {
          q"gopher.asyncApply1(${fun})(${anb})"
       } else {
          //we can't call macros here, becouse we don't know types of implicitParams
          //val a = param.tpe
          //val b = body.tpe
          //AsyncApply.impl1i(c)(fun)(anb,implicitParams)(a,b)
          q"gopher.goasync.AsyncApply.apply1i(${fun})(${anb},${implicitParams})"
          //q"gopher.asyncApply1i(${fun})(${anb})(..$implicitParams)"
       }
       // typecheck is necessory
       //  1. to prevent runnint analysis of async over internal awaits in anb as on 
       //    enclosing async instead those applied from asyncApply
       //  2. to expand macroses here, to prevent error during expanding macroses
       //    in next typecheck
       c.typecheck(uar)
    }
    //typecheck with macros disabled is needed for compiler,
    //to set symbol 'await', because async macro discovered
    //awaits by looking at symbols
    val r = c.typecheck(q"scala.async.Async.await(${ar})",withMacrosDisabled=true)
    r
   }

   def findAwait(c:Context)(body:c.Tree): Boolean = 
   {
    import c.universe._
    var found: Boolean = false
    val transformer = new Transformer {

      override def transform(tree:Tree):Tree =
      {
         if (found) 
          tree
         else {
           tree match {
             case q"(scala.async.Async.await[${w}]($r)):${w1}"=>
                     found = true
                     tree
             case q"scala.async.Async.await[${w}]($r)"=>
                     found = true
                     tree
             case q"(scala.async.Async.async[${w}]($r)):${w1}"=>
                     //TODO: add test to test-case
                     tree
             case q"(${a}=>${b})" =>
                     // don't touch nested functions
                     tree
             case _ => 
                  super.transform(tree)
            }
         }
      }

    }
    transformer.transform(body)
    found
   }

   private def numberOfParamLists(c:Context)(obj:c.Tree,m:c.Name):Int =
   {
    ???
   }

}

