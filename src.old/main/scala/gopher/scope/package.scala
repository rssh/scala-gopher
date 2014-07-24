package gopher

import language.experimental.macros

package object scope 
{

  def goScope[A](x: =>A): A = macro ScopeMacroses.goScopeImpl[A]
  
  def goScoped[A](x: =>A)(implicit sc:ScopeContext) : A
   =   sc.eval( x )
    
  def _defer[A](x: =>Unit)(implicit sc: ScopeContext) =
     sc.pushDefer(x)

  def _panic[A](s:String)(implicit sc: ScopeContext): Unit = 
         _panic(new PanicException(s,sc))

  def _panic[E <: Throwable, A](e: E)(implicit sc: ScopeContext): Unit =
         { throw e }

  def _recover[A](r:A)(implicit sc: ScopeContext): Unit =
         sc.recover(r)

  def _suppressedExceptions[A](implicit sc: ScopeContext): Seq[Exception] =
         sc.suppressedExceptions

  def _throwSuppressed[A](implicit sc:ScopeContext): Unit =
         sc.throwSuppressed

}
