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
       val nbody = transformDefer(c)(body)
       c.Expr[Future[T]](q"""{implicit val defered = new Defered()
                              val retval = scala.async.Async.async(${nbody})(${ec}).andThen(defered.run(_))
                              retval
                             }
                          """)
     } else {
       c.Expr[Future[T]](q"scala.async.Async.async(${body})(${ec})")
     }
   }

   def goScope[T:c.WeakTypeTag](c:Context)(body:c.Expr[T])(ec:c.Expr[ExecutionContext]):c.Expr[T] =
   {
     import c.universe._
     if (containsDefer(c)(body)) {
       val nbody = transformDefer(c)(body)
       c.Expr[T](q"""{implicit val defered = new Defered()
                      defered.run(Try(${body}))
                     }""")
     } else {
       body
     }
   }

   def containsDefer[T](c:Context)(body:c.Expr[T]):Boolean = false

   def transformDefer[T](c:Context)(body:c.Expr[T]):c.Expr[T] = ???


}

