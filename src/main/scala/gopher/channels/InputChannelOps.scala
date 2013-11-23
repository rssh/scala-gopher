package gopher.channels

import scala.concurrent._
import akka.actor._

import ops._


trait InputChannelOps[API <: ChannelsAPI[API], +A] extends ChannelBase[API] 
{

  this: API#IChannel[A] =>
  
     
  def readZipped[B](c:Iterable[B])(f: (A,B)=>Unit): Tie[API]  = 
  {
    makeTie.addReadAction(this, new ReadZipped(c.iterator,f)).start()
  }
  
  def readWhile(p: A => Boolean)(f: A => Unit): Tie[API] =
  {
    makeTie.addReadAction(this, new ReadWhile(p,f)).start() 
  }
  
  def foldWhile[S](s:S)(p: (A,S)=>Boolean)(f:(A,S)=>S): Future[S] = 
  {
    val promise = Promise[S]();
    api.makeTie.addReadAction(this,new FoldWhile(s,p,f,promise)).start();
    promise.future
  }

  def ffoldWhile[S](s:S)(p: (A,S)=>Boolean)(f:(A,S)=>Future[S]): Future[S] = 
  {
    val promise = Promise[S]();
    api.makeTie.addReadAction(this,new FFoldWhile(s,p,f,promise)).start();
    promise.future
  }
  
  //TODO:
  //def ffold[S](s:S)(f:(A,S)=>Future[Option[S]]):Future[S] = ???
  
    
  
}