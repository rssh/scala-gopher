
import language.experimental.macros

import scala.concurrent.Future
import scala.reflect.macros.Context

package object gopher 
{

  def go[A](x: =>A):Future[A] = macro goImpl[A]

  def goImpl[A](c:Context)(x: c.Expr[A]):c.Expr[Future[A]] =
  {
   import c.universe._
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
  def defer(x: =>Unit): Unit = ???  
     
  @compileTimeOnly("recover outside of go or goScope block")
  def recover[A](x: A): Unit = ???  

  @compileTimeOnly("panic outside of go or goScope block")
  def panic(x: String): Unit = ??? 

  @compileTimeOnly("suppressedExceptions outside of go or goScope block")
  def suppressedExceptions: List[Exception] = ???
  
  @compileTimeOnly("throwSuppressed outside of go or goScope block")
  def throwSuppressed: Unit = ???
  
  
}
