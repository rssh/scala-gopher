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
    val transformer = new Transformer {
      override def transform(tree:Tree):Tree =
       tree match {
         case q"${f1}(${a}=>${b})" =>
            val found = findAwait(c)(b)
            if (found) {
                //System.err.println(s"found hof entry ${f1} ${a}=>${b}")
                val btype = b.tpe
                // untypechack is necessory, because async-transform later will corrupt
                //  symbols owners inside b
                // [scala-2.11.8]
                val nb = c.untypecheck(b)
                val anb = atPos(b.pos){
                              // typecheck here is needed to prevent incorrect liftingUp of
                              // internal variables in ${b}
                              //[scala-2.11.8]
                              //c.typecheck(q"(${a})=>go[${btype}](${nb})")
                              val nnb = transformAsyncBody(c)(nb)
                              //c.typecheck(q"(${a})=>scala.async.Async.async[${btype}](${nnb})")
                              q"(${a})=>scala.async.Async.async[${btype}](${nnb})"
                          }
                val ar = atPos(tree.pos){
                          // typecheck is necessory
                          //  1. to prevent runnint analysis of async over internal awaits in anb as on 
                          //    enclosing async instead those applied from asyncApply
                          //  2. to expand macroses here, to prevent error during expanding macroses
                          //    in next typecheck
                          c.typecheck(q"gopher.asyncApply1(${f1})(${anb})")
                          //q"gopher.asyncApply1(${f1})(${anb})"
                        }
                //typecheck with macros disabled is needed for compiler,
                //to set symbol 'await', because async macro discovered
                //awaits by looking at symbole
                val r = c.typecheck(q"scala.async.Async.await[${btype}](${ar})",withMacrosDisabled=true)
                r
            } else {
                super.transform(tree)
            }
         case _ =>
                super.transform(tree)
       }
    }
    transformer.transform(body)
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
                     System.err.println(s"found await: [${w}](${r})")
                     found = true
                     // here we erase 'await' symbols
                     //q"(scala.async.Async.await[${w}]($r)):${w1}"
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


}

