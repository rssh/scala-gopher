package gopher.channels

import scala.concurrent._

sealed trait ReadSelectorArgument[A,B]
{
  def normalizedFun: (A, ContRead[A,B]) => Option[Future[Continuated[B]]]
}

case class AsyncFullSelectedArgument[A,B](
                   f: (A, ContRead[A,B]) => Option[Future[Continuated[B]]]
              )  extends ReadSelectorArgument[A,B]
{
  override def normalizedFun = f
}

class SelectorBuilder[A](api: API)
{


   def onRead[E](ch:Input[E])(arg: ReadSelectorArgument[E,A]): this.type =
   {
     selector.addReader(ch,arg.normalizedFun,priority)
     priority += 1
     this
   }

   var selector=new Selector[A](api)
   var priority = 1
}


class ForeverSelectorBuilder(api: API) extends SelectorBuilder[Unit](api)
{

}
