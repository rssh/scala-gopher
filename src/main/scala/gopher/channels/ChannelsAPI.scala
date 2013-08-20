package gopher.channels

import scala.language.experimental.macros._
import scala.reflect.macros._
import scala.reflect._
import scala.concurrent._
import scala.concurrent.duration._

trait ChannelsAPI {

  type IChannel[A] <: InputChannel[A]
  type OChannel[A] <: OutputChannel[A]
  type IOChannel[A] <: InputOutputChannel[A]
    
  def makeChannel[A: ClassTag](capacity: Int)(implicit ec: ExecutionContext): IOChannel[A]
  
  type GTie <: Tie
  
  def makeTie(implicit ec:ExecutionContext): GTie
  
  type GFuture[A] <: Future[A]
  
  def  gAwait[A](f: GFuture[A], d: Duration)(implicit ec: ExecutionContext): A
 
  def  transformGo[A](c:Context)(code: c.Expr[A]): c.Expr[GFuture[A]]
    
}