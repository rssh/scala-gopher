package gopher.channels

import gopher._
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentLinkedQueue


class Selector[A](api: GopherAPI) extends PromiseFlowTermination[A]
{

  thisSelector =>

  def addReader[E](ch:Input[E],f: ContRead[E,A] => Option[ContRead.In[E]=>Future[Continuated[A]]]): Unit = ???
 
  def addWriter[E](ch:Output[E], f: ContWrite[E,A] => Option[(E,Future[Continuated[A]])]): Unit = ???

  def addIdleSkip(f: Skip[A] => Option[Future[Continuated[A]]]): Unit = ???

  def run:Future[A] = ???

  private[channels]  def startIdles: Unit = ???

  private[this] def makeLocked(block: Continuated[A]): Continuated[A] = ???
  
  def isLocked: Boolean = ???


}




