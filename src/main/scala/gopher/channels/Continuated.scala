package gopher.channels;

import scala.concurrent._
import java.util.concurrent.atomic.AtomicBoolean
import gopher._

/**
 * represent continuated computation from A to B.
 */
sealed trait Continuated[+A]
{
 type R =  X forSome { type X <: A @annotation.unchecked.uncheckedVariance }
}

sealed trait FlowContinuated[A] extends Continuated[A]
{
  def flowTermination: FlowTermination[A]
}

case class Done[A](result:A, override val flowTermination: FlowTermination[A]) extends FlowContinuated[A]

/**
 * read A and compute B as result.
 */
case class ContRead[A,B](function: ContRead[A,B] => Option[(()=>A) => Future[Continuated[B]]], channel: Input[A], override val flowTermination: FlowTermination[B]) extends FlowContinuated[B]
{
  type El = A
}

object ContRead
{

  sealed trait In[+A]
  case class NextValue[A](a:A) extends In[A]
  case object SkipValue extends In[Nothing]
  case object ChannelClosed extends In[Nothing]
  case class Failure(ex:Exception) extends In[Nothing]
  
}


/**
 * write A and compute B as result
 */
case class ContWrite[A,B](function: ContWrite[A,B] => Option[(A,Future[Continuated[B]])], channel: Output[A], override val flowTermination: FlowTermination[B]) extends FlowContinuated[B]
{
  type El = A
}

/**
 * skip (i.e. do some operation not related to reading or writing.)
 */
case class Skip[A](function: Skip[A] => Option[Future[Continuated[A]]], override val flowTermination: FlowTermination[A]) extends FlowContinuated[A]

/**
 * never means the end of conversation
 */
case object Never extends Continuated[Nothing]



