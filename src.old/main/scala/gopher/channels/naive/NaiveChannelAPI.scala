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
    
  override def makeChannel[A: ClassTag](capacity: Int, name: String)(implicit 
                                                                   ecp: ChannelsExecutionContextProvider = DefaultChannelsExecutionContextProvider, 
                                                                   asp: ChannelsActorSystemProvider = DefaultChannelsActorSystemProvider,
                                                                   lf:  ChannelsLoggerFactory = DefaultChannelsLoggerFactory): IOChannel[A] =
                                            new GBlockedQueue[A](capacity,name,ecp,asp,lf);
  
  type GTie = NaiveTie
  
  override def makeTie(name:String)(implicit ecp:ChannelsExecutionContextProvider=DefaultChannelsExecutionContextProvider, 
                                             asp: ChannelsActorSystemProvider = DefaultChannelsActorSystemProvider,
                                             clf:  ChannelsLoggerFactory = DefaultChannelsLoggerFactory
                                              ): GTie =
    new SelectorContext(name,ecp,asp,clf)
  
  type GFuture[T, A] = Future[A]
 
  def  gAwait[A](f: GFuture[NaiveChannelsAPI, A], d: Duration)(implicit ec: ExecutionContext, as: ActorSystem) =
    Await.result(f, d)
 
  def  transformGo[A](c:Context)(code: c.Expr[A]): c.Expr[GFuture[NaiveChannelsAPI,A]] =
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

