package gopher.goasync

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import gopher._
import gopher.util._
import scala.concurrent._
import scala.annotation.unchecked._


/**
 * async arround go. 
 *
 * Basicly go is wrapped inside SIP-22 async with defer
 */
object GoAsync
{

 //TODO: add handling of try/catch and operations inside collections.

   def goImpl[T:c.WeakTypeTag](c:Context)(body:c.Expr[T])(ec:c.Expr[ExecutionContext]):c.Expr[Future[T]] =
   {
     import c.universe._
     if (containsDefer(c)(body)) {
       val defers = TermName(c.freshName)
       val promise = TermName(c.freshName)
       // asyn transform wantstyped tree on entry, so we must substitute 'defers' to untyped 
       // values after it, no before.
       c.Expr[Future[T]](
                         q"""
                             gopher.goasync.GoAsync.transformDeferMacro[${c.weakTypeOf[T]}](
                               {implicit val ${defers} = new Defers[${c.weakTypeOf[T]}]()
                                val ${promise} = Promise[${c.weakTypeOf[T]}]()
                                scala.async.Async.async(${body})(${ec}).onComplete( x =>
                                     ${promise}.complete(${defers}.tryProcess(x))
                                )(${ec})
                                ${promise}.future
                               }
                             )
                          """)
     } else {
       c.Expr[Future[T]](q"scala.async.Async.async(${body})(${ec})")
     }
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

   
}

