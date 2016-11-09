package gopher.goasync

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import scala.concurrent._

object AsyncWrapper
{

   def async[T](x:T)(implicit ec:ExecutionContext):Future[T] = macro asyncImpl[T]

   def await[T](x:Future[T]):T = macro awaitImpl[T]

   def postWrap[T](x:T):T = macro postWrapImpl[T]
   
   def asyncImpl[T](c:Context)(x:c.Expr[T])(ec:c.Expr[ExecutionContext]):c.Expr[Future[T]] =
   {
    import c.universe._
    c.Expr[Future[T]](q"gopher.goasync.AsyncWrapper.postWrap(scala.async.Async.async(${x})(${ec}))")
   }

   def awaitImpl[T](c:Context)(x:c.Expr[Future[T]]):c.Expr[T] =
   {
    import c.universe._
    c.Expr[T](q"gopher.goasync.AsyncWrapper.postWrap(scala.async.Async.await(${x}))")
   }

   def postWrapImpl[T](c:Context)(x:c.Expr[T]):c.Expr[T]=
   {
    import c.universe._
    x
   }

}
