package gopher.channels.naive

import language.experimental.macros


import gopher.channels._
import scala.concurrent._
import scala.concurrent.duration._
import scala.reflect._
import scala.reflect.macros.Context
import akka.actor._


class NaiveChannelsAPI extends ChannelsAPI[NaiveChannelsAPI]
{

  type IChannel[+A] = NaiveInputChannel[A]
  type OChannel[-A] = NaiveOutputChannel[A]
  type IOChannel[A] = GBlockedQueue[A]
    
  def makeChannel[A: ClassTag](capacity: Int)(implicit ec: ExecutionContext): IOChannel[A] =
                                            new GBlockedQueue[A](capacity,ec);
  
  type GTie = NaiveTie
  
  def makeRealTie(implicit ec:ExecutionContext, as: ActorSystem): GTie =
    new SelectorContext()
  
  type GFuture[A] = Future[A]
 
  def  gAwait[A](f: GFuture[A], d: Duration)(implicit ec: ExecutionContext) =
    Await.result(f, d)
 
  def  transformGo[A](c:Context)(code: c.Expr[A]): c.Expr[GFuture[A]] =
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
                     List(c.resetAllAttrs(code.tree))
                  )
                )
              )
                      
    c.Expr[Future[A]](tree)           
  }
   
  
  def  transformForSelect(c:Context)(code: c.Expr[NaiveChannelsAPI#GTie => Unit]): c.Expr[Unit] =
    SelectorMacroCaller.foreachImpl(c)(code)
  
  def  transformForSelectOnce(c:Context)(code: c.Expr[NaiveChannelsAPI#GTie => Unit]): c.Expr[Unit] =
    SelectorMacroCaller.foreachOnceImpl(c)(code)
 
 
  
}

object NaiveChannelsAPI {
  val instance = new NaiveChannelsAPI
} 

