package gopher.channels

import gopher._
import scala.concurrent._

sealed trait ReadSelectorArgument[A,B]
{
  def normalizedFun: (A, ContRead[A,B]) => Option[Future[Continuated[B]]]
}

case class AsyncFullReadSelectorArgument[A,B](
                   f: (A, ContRead[A,B]) => Option[Future[Continuated[B]]]
              )  extends ReadSelectorArgument[A,B]
{
  def normalizedFun = f
}

case class AsyncNoOptionReadSelectorArgument[A,B](
                   f: (A, ContRead[A,B]) => Future[Continuated[B]]
               ) extends ReadSelectorArgument[A,B]
{
  def normalizedFun = ( (a,c) => Some(f(a,c)) )
}

case class SyncReadSelectorArgument[A,B](
                   f: (A, ContRead[A,B]) => Continuated[B]
               ) extends ReadSelectorArgument[A,B]
{
  def normalizedFun = ( (a,c) => Some( Future successful f(a,c) ) )
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

class SelectorBuilder[A](api: GopherAPI)
{


   def onRead[E](ch:Input[E])(arg: ReadSelectorArgument[E,A]): this.type =
   {
     selector.addReader(ch,arg.normalizedFun)
     this
   }

   def onWrite[E](ch:Output[E])(arg: WriteSelectorArgument[E,A]): this.type =
   {
     selector.addWriter(ch,arg.normalizedFun)
     this
   }

   def onIdle(arg: SkipSelectorArgument[A]): this.type =
   {
     selector.addSkip(arg.normalizedFun)
     this
   }


   def go: Future[A] = selector.run

   implicit def ec: ExecutionContext = api.executionContext

   val selector=new Selector[A](api)

}


class ForeverSelectorBuilder(api: GopherAPI) extends SelectorBuilder[Unit](api)
{

   def onReadAsync[E](ch:Input[E])(f: E => Future[Unit] ): this.type =
   {
     // TODO: check that channel is closed or flowTermination is terminated
     val f1: ((E,ContRead[E,Unit]) => Option[Future[Continuated[Unit]]]) =
           { (e, cr) => Some(f(e) map Function.const(cr)) }
     selector.addReader(ch,f1) 
     this
   }


   def onWriteAsync[E](ch:Output[E])(f: => (E, Future[Unit])): this.type =
   {
     // TODO: check that channel is closed or flowTermination is terminated
     val f1: (ContWrite[E,Unit] => Option[(E,Future[Continuated[Unit]])]) =
          { cw =>  
             val (e,n) = f
             Some((e, n map Function.const(cw)))
          }
     selector.addWriter(ch, f1)
     this
   }

   def onDefaultAsync(f: => Future[Unit]): this.type =
   {
     val f1: Skip[Unit] => Option[Future[Continuated[Unit]]] =
       { s => Some(f map Function.const(s)) }
     selector.addSkip(f1)
     this
   }

//   def onRead[E](ch:Input[E])(f: (e:E) => Unit ): this.type =
//            macro ForeverSelectorBuilder.onReadImpl
 

}

object ForeverSelectorBuilder
{

}

class OnceSelectorBuilder[+A](api: GopherAPI) extends SelectorBuilder[A@annotation.unchecked.uncheckedVariance](api)
{

   def onReadAsync[E, B >: A](ch:Input[E])(f: E => Future[B] ): 
                                           OnceSelectorBuilder[B] =
   {
     val f1: ((E,ContRead[E,B]) => Option[Future[Continuated[B]]]) =
           { (e, cr) => Some(f(e) map( Done(_,cr.flowTermination))) }
     selector.asInstanceOf[Selector[B]].addReader(ch,f1) 
     this.asInstanceOf[OnceSelectorBuilder[B]]
   }

}


class SelectFactory(api: GopherAPI)
{
 
  /**
   * forever builder. 
   *@Seee ForeverSelectorBuilder
   */
  def forever: ForeverSelectorBuilder = new ForeverSelectorBuilder(api)

  def once: OnceSelectorBuilder[Nothing] = new OnceSelectorBuilder(api)

  /**
   * generic selector builder
   */
  def loop[A]: SelectorBuilder[A] = new SelectorBuilder[A](api)
}

