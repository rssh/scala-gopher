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


class SelectorBuilder[A](api: GopherAPI)
{


   def onRead[E](ch:Input[E])(arg: ReadSelectorArgument[E,A]): this.type =
   {
     selector.addReader(ch,arg.normalizedFun,priority)
     priority += 1
     this
   }

   def go: Future[A] = selector.run

   implicit def ec: ExecutionContext = api.executionContext

   var selector=new Selector[A](api)
   var priority = 1


}


class ForeverSelectorBuilder(api: GopherAPI) extends SelectorBuilder[Unit](api)
{

   def onReadAsync[E](ch:Input[E])(f: E => Future[Unit] ): this.type =
   {
     // TODO: check that channel is closed or flowTermination is terminated
     val f1: ((E,ContRead[E,Unit]) => Option[Future[Continuated[Unit]]]) =
           { (e, cr) => Some(f(e) map Function.const(cr)) }
     selector.addReader(ch,f1,priority) 
     priority += 1
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
     selector.addWriter(ch, f1, priority)
     priority += 1
     this
   }

   def onDefaultAsync(f: => Future[Unit]): this.type =
   {
     val f1: Skip[Unit] => Option[Future[Continuated[Unit]]] =
       { s => Some(f map Function.const(s)) }
     selector.addSkip(f1,Int.MaxValue)
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
           { (e, cr) => Some(f(e) map( Done(_,cr.flwt))) }
     selector.asInstanceOf[Selector[B]].addReader(ch,f1,priority) 
     this.asInstanceOf[OnceSelectorBuilder[B]]
   }

}


class SelectFactory(api: GopherAPI)
{
  def loop: ForeverSelectorBuilder = new ForeverSelectorBuilder(api)

  def once: OnceSelectorBuilder[Nothing] = new OnceSelectorBuilder(api)
}
