package gopher.channels

import gopher._
import scala.concurrent._

sealed trait ReadSelectorArgument[A,B]
{
  def normalizedFun: ContRead[A,B] => Option[ContRead.In[A]=>Future[Continuated[B]]]
}

case class AsyncFullReadSelectorArgument[A,B](
                   f: ContRead[A,B] => Option[ContRead.In[A]=>Future[Continuated[B]]]
              )  extends ReadSelectorArgument[A,B]
{
  def normalizedFun = f
}

case class AsyncNoOptionReadSelectorArgument[A,B](
                   f: ContRead[A,B] => (ContRead.In[A]=>Future[Continuated[B]])
               ) extends ReadSelectorArgument[A,B]
{
   def normalizedFun = ( cont => Some(f(cont)) )
}

case class AsyncNoGenReadSelectorArgument[A,B](
                   f: ContRead[A,B] => (A=>Future[Continuated[B]])
               ) extends ReadSelectorArgument[A,B]
{
   def normalizedFun = ( cont => Some(ContRead.liftIn(cont)(f(cont))) )
}

case class AsyncPairReadSelectorArgument[A,B](
                   f: (A, ContRead[A,B]) => Future[Continuated[B]]
               ) extends ReadSelectorArgument[A,B]
{
   def normalizedFun = ( c => Some(ContRead.liftIn(c)(f(_,c))) ) 
}

case class SyncReadSelectorArgument[A,B](
                   f: ContRead[A,B] => (ContRead.In[A] => Continuated[B])
               ) extends ReadSelectorArgument[A,B]
{
  def normalizedFun = ( cont => Some( gen => Future successful f(cont)(gen) ) )
}

case class SyncPairReadSelectorArgument[A,B](
                   f: (A, ContRead[A,B]) => Continuated[B]
               ) extends ReadSelectorArgument[A,B]
{
   def normalizedFun = ( c => Some(ContRead.liftIn(c)(a => Future successful f(a,c))) )
}

sealed trait WriteSelectorArgument[A,B]
{
  def normalizedFun: ContWrite[A,B] => Option[(A,Future[Continuated[B]])]
}

case class AsyncFullWriteSelectorArgument[A,B](
                   f: ContWrite[A,B] => Option[(A,Future[Continuated[B]])]
              )  extends WriteSelectorArgument[A,B]
{
  def normalizedFun = f
}

case class AsyncNoOptWriteSelectorArgument[A,B](
                   f: ContWrite[A,B] => (A,Future[Continuated[B]])
              )  extends WriteSelectorArgument[A,B]
{
  def normalizedFun = (c => Some(f(c)))
}

case class SyncWriteSelectorArgument[A,B](
                   f: ContWrite[A,B] => (A,Continuated[B])
              )  extends WriteSelectorArgument[A,B]
{
  def normalizedFun = {c => 
     val (a, next) = f(c) 
     Some((a,Future successful next))
  }

}

sealed trait SkipSelectorArgument[A]
{
  def normalizedFun: Skip[A] => Option[Future[Continuated[A]]]
}

case class AsyncFullSkipSelectorArgument[A](
                   f: Skip[A] => Option[Future[Continuated[A]]]
              )  extends SkipSelectorArgument[A]
{
  def normalizedFun = f
}

case class AsyncNoOptSkipSelectorArgument[A](
                   f: Skip[A] => Future[Continuated[A]]
              )  extends SkipSelectorArgument[A]
{
  def normalizedFun = { c => Some(f(c)) }
}

case class SyncSelectorArgument[A](
                   f: Skip[A] => Continuated[A]
              )  extends SkipSelectorArgument[A]
{
  def normalizedFun = { c => Some(Future successful f(c)) }
}


