
import language.experimental.macros

import scala.concurrent.Future
import scala.reflect.macros.Context

import gopher.channels._
import gopher.util._

/**
  * package wich introduce go-like language constructions into scala:
  * 
  * goroutines 
  *  <ul>
  *   <li>[[gopher.go go]] </li>
  *  </ul>
  *  
  *  go scope support:
  *   <ul>
  *    <li> [[gopher.goScope goScope]] </li>
  *    <li> [[gopher.defer defer]] </li>  
  *    <li> [[gopher.panic panic]] </li>   
  *    <li> [[gopher.recover recover]] </li>
  *   </ul>
  *  
  *    
  *  channels and select statement support:
  *   <ul>
  *    <li> [[gopher.makeChannel makeChannel]] </li>
  *    <li> [[gopher.select select]] </li>
  *  </ul>
  *   
  */
package object gopher 
{

  /**
   * spawn execution of x (wrapped into goScope) in separate execution flow.
   */
  def go[A](x: =>A):Future[A] = macro goImpl[A]

  /**
   * implementation of go. public as imlementation details.
   * @see go
   */
  def goImpl[A](c:Context)(x: c.Expr[A]):c.Expr[Future[A]] =
    MacroHelper.implicitChannelApi(c).transformGo(c)(x)

 
  /**
   * select pseudoobject -- used for emulation of go 'select' statements via for-comprehancions.
   * i.e. next go code:
   * {{{
   * for(;;) {
   *  select
   *    case channelA -> x : do-something-with-x
   *    case channelB -> y : do-something-with-y
   * }   
   * }}}
   *  will looks in scala as
   * <pre>
   * for(s <- select) 
   *  s match {
   *    case `channelA` ~> (x: XType) => do-something-with-x
   *    case `channelB` ~> (y: YType) => do-something-with-y
   *  }
   * </pre>
   * and plain select (without enclosing loop) as
   * for(s <- select.once) 
   *  s match {
   *    case `channelA` ~> (x: XType) => do-something-with-x
   *    case `channelB` ~> (y: YType) => do-something-with-y
   *  }
   * </pre>
   * @see [[gopher.channels.SelectorContext]]
   * @see [[gopher.~>]]
   */
  def select[API <: ChannelsAPI[API]](implicit api: API) = new channels.ForSelectTransformer[API]
  
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
    def unapply(s: gopher.channels.naive.SelectorContext): Option[InputChannelPair[_]] = ??? //macro unapplyImpl
        
  }
  
  /**
   * unapply pattern for read case statement in select loop
   * <code> channel ? x </code> transformed to reading from channel
   * into variable x (end evaluating block in case statement if read was successful)
   */  
  object ? 
  {
    @compileTimeOnly("? unapply must be used only in select for loop")
    def unapply(s: gopher.channels.naive.SelectorContext): Option[InputChannelPair[_]] = ???
  }
  
  /**
   * unapply pattern for write case statement in select loop
   * <code> `channel` <~ `x` </code> transformed to write from channel
   * into variable x (and evaluating block if write was successful).
   */    
  object <~
  {
    @compileTimeOnly("<~ unapply must be used only in select for loop")   
    def unapply(s: gopher.channels.naive.SelectorContext): Option[OutputChannelPair[_]] = ???
  }

  /**
   * unapply pattern for write case statement in select loop
   * <code> `channel` <~ `x` </code> transformed to write from channel
   * into variable x (and evaluating block if write was successful).
   */      
  object ! 
  {
    @compileTimeOnly("! unapply must be used only in select for loop")   
    def unapply(s: gopher.channels.naive.SelectorContext): Option[OutputChannelPair[_]] = ???
  }
  
  import scope.ScopeMacroses
  
  /**
   * block of code inside goScope is processed for support of 'defer', 'panic' and 'recover' constructions.
   */
  def goScope[A](x: =>A): A = macro ScopeMacroses.goScopeImpl[A]
  
  import scope.ScopeContext
  import scope.PanicException
    
  /**
   * defer statement: push x to be executed at the end of [[gopher.goScope goScope]] or [[gopher.go go]] block. 
   */
  @compileTimeOnly("defer outside of go or goScope block")
  def defer(x: =>Unit): Unit = ???  
     
  /**
   * recover statement: if x (situated inside defer block) will be executed in process of exception handling,
   *  inside go scope block, than block will return value of x instead throwing exception.
   */
  @compileTimeOnly("recover outside of go or goScope block")
  def recover[A](x: A): Unit = ???  

  /**
   * throw PanicException
   */
  @compileTimeOnly("panic outside of go or goScope block")
  def panic(x: String): Unit = ??? 

  /**
   * Access to list of exceptions from defer blocks, which was suppressed during handling of some other 'first' exception.
   */
  @compileTimeOnly("suppressedExceptions outside of go or goScope block")
  def suppressedExceptions: List[Exception] = ???
  
  /**
   * throw suppresses exception instead first.
   */
  @compileTimeOnly("throwSuppressed outside of go or goScope block")
  def throwSuppressed: Unit = ???
  
  import scala.concurrent._
  import scala.reflect._
  
  
  /**
   * Make channel: create go-like channel with given capacity.
   */
  @inline
  def makeChannel[A:ClassTag](capacity: Int = 1)(implicit ec:ExecutionContext, api:ChannelsAPI[_]): api.IOChannel[A] = {
    api.makeChannel[A](capacity)
  }

  /**
   * Make channel: create go-like channel with given capacity.
   */
  @inline
  def makeTie[A:ClassTag, API <: ChannelsAPI[API]](implicit api: API, ec: ExecutionContext): Tie[API] =
    api.makeTie
     

  implicit def toOutputPut[API <: ChannelsAPI[API],A](ch: API#OChannel[A]) = new OutputPut(ch)
  
}
