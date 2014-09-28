package gopher.goasync

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import gopher._
import gopher.util._
import scala.concurrent._
import scala.annotation.unchecked._


/**
 * async arround go. Just wrapper arround SIP-22 async
 *
 */
object GoAsync
{

 //TODO: add handling of try/catch and operations inside collections.

   def goImpl[T](c:Context)(body:c.Expr[T])(ec:c.Expr[ExecutionContext]):c.Expr[Future[T]] =
   {
     import c.universe._
     c.Expr[Future[T]](q"scala.async.Async.async(${body})(${ec})")
   }

}

