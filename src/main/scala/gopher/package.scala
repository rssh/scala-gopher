
import language.experimental.macros

import scala.concurrent.Future
import scala.reflect.macros.Context

package object gopher 
{

  def go[A](x: =>A):Future[A] = macro goImpl[A]

  def goImpl[A](c:Context)(x: c.Expr[A]):c.Expr[Future[A]] =
  {
   import c.universe._
   System.err.println("goImpl, x="+x)
   System.err.println("goImpl, row="+showRaw(x))
   //
   //  Future {
   //     goScope(
   //        x
   //     )
   //  }
   val tree = Apply(
                Select(
                    Select(
                        Ident(newTermName("scala")), 
                        newTermName("concurrent")), 
                    newTermName("Future")),    
                List(    
                  Apply(
                    Select(
                            Select(
                                Ident(nme.ROOTPKG), 
                                newTermName("gopher")),  
                            newTermName("goScope")), 
                     List(c.resetAllAttrs(x.tree))
                  )
                )
              )
                      
   System.err.println("goImpl, output="+tree)

   c.Expr[Future[A]](tree)           
  }

  val select = channels.SelectorMacroCaller

  import scala.reflect.internal.annotations.compileTimeOnly
  
  object ~>
  {
    
    @compileTimeOnly("~> unapply must be used only in select for loop")
    def unapply(s: channels.SelectorContext): Option[(channels.InputChannel[Any],Any)] = ??? //macro unapplyImpl
        
  } 

  object ? 
  {
    @compileTimeOnly("? unapply must be used only in select for loop")
    def unapply(s: channels.SelectorContext): Option[(channels.InputChannel[Any],Any)] = ???
  }
  
  object <~
  {
    @compileTimeOnly("<~ unapply must be used only in select for loop")   
    def unapply(s: channels.SelectorContext): Option[(channels.InputChannel[Any],Any)] = ???
  }

  object ! 
  {
    @compileTimeOnly("! unapply must be used only in select for loop")   
    def unapply(s: channels.SelectorContext): Option[(channels.InputChannel[Any],Any)] = ???
  }
  
  import scope.ScopeMacroses
  def goScope[A](x: =>A): A = macro ScopeMacroses.goScopeImpl[A]
  
  import scope.ScopeContext
  import scope.PanicException
    
  @compileTimeOnly("defer outside of go or goScope block")
  def defer[A](x: =>Unit): A = ???
   

    
  @inline def panic[A](s:String)(implicit sc: ScopeContext[A]): Unit = 
         panic(new PanicException[A](s,sc))

  @inline def panic[E <: Throwable, A](e: E)(implicit sc: ScopeContext[A]): Unit =
         { throw e }

  @inline def recover[A](r:A)(implicit sc: ScopeContext[A]): Unit =
         sc.recover(r)

  @inline def suppressedExceptions[A](implicit sc: ScopeContext[A]): Seq[Exception] =
         sc.suppressedExceptions

  @inline def throwSuppressed[A](implicit sc:ScopeContext[A]): Unit =
         sc.throwSuppressed
  
  
}
