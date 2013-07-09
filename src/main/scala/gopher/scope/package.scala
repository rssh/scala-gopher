package gopher

import language.experimental.macros

package object scope 
{

  def goScope[A](x: =>A): A = macro ScopeMacroses.goScopeImpl[A]
  
  def goScoped[A](x: =>A)(implicit sc:ScopeContext[A]) : A
   =   sc.eval( x )
    
  def defer[A](x: =>Unit)(implicit sc: ScopeContext[A]) =
     sc.pushDefer(x)

  def panic[A](s:String)(implicit sc: ScopeContext[A]): Unit = 
         panic(new PanicException[A](s,sc))

  def panic[E <: Throwable, A](e: E)(implicit sc: ScopeContext[A]): Unit =
         { throw e }

  def recover[A](r:A)(implicit sc: ScopeContext[A]): Unit =
         sc.recover(r)

  def suppressedExceptions[A](implicit sc: ScopeContext[A]): Seq[Exception] =
         sc.suppressedExceptions

  def throwSuppressed[A](implicit sc:ScopeContext[A]): Unit =
         sc.throwSuppressed

}
