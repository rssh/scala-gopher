
import language.experimental.macros

import scala.concurrent.Future
import scala.reflect.macros.Context

/**
 * package wich introduce go-like language constructions into scala.
 */
package object gopher 
{

  /**
   * spawn execution of x (wrapped into goScope) in separate execution flow.
   */
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
  
  
  type InputChannelPair[A] = Tuple2[channels.InputChannel[A], A]
  type OutputChannelPair[A] = Tuple2[channels.InputChannel[A], A]
  
  
  
  
  /**
   * unapply pattern for read case statement in select loop
   * <code> channel ~> x </code> transformed to reading from channel
   * into variable x.
   */
  object ~>
  {
    
    @compileTimeOnly("~> unapply must be used only in select for loop")
    def unapply(s: channels.SelectorContext): Option[InputChannelPair[_]] = ??? //macro unapplyImpl
        
  }
  
  
  
  object ? 
  {
    @compileTimeOnly("? unapply must be used only in select for loop")
    def unapply(s: channels.SelectorContext): Option[InputChannelPair[_]] = ???
  }
  
  object <~
  {
    @compileTimeOnly("<~ unapply must be used only in select for loop")   
    def unapply(s: channels.SelectorContext): Option[OutputChannelPair[_]] = ???
  }

  object ! 
  {
    @compileTimeOnly("! unapply must be used only in select for loop")   
    def unapply(s: channels.SelectorContext): Option[OutputChannelPair[_]] = ???
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
  
  import scala.concurrent._
  import scala.reflect._
  
  
  //
  @inline
  def makeChannel[A:ClassTag](capacity: Int = 1000)(implicit ec:ExecutionContext) = channels.make(capacity)

  // interaction with actors
  import akka.actor._
  
  @inline
  def bindChannelRead[A](read: channels.InputChannel[A], actor: ActorRef): Unit =
       channels.bindRead(read,actor)
  
  @inline     
  def bindChannelWrite[A: ClassTag](write: channels.OutputChannel[A], name: String)(implicit as: ActorSystem): ActorRef =
       channels.bindWrite(write, name)
  
  
}
