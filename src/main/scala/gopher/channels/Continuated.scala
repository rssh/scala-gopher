package gopher.channels;

import scala.concurrent._
import java.util.concurrent.atomic.AtomicBoolean

/**
 * represent continuated computation from A to B.
 */
sealed trait Continuated[+A]
{
 type R =  X forSome { type X <: A @annotation.unchecked.uncheckedVariance }
}


case class Done[A](r:A) extends Continuated[A]

/**
 * read A and compute B as result.
 */
case class ContRead[A,B](f: (A, ContRead[A,B]) => Future[Continuated[B]], ch: Input[A]) extends Continuated[B]
{
  type El = A
}


/**
 * write A and compute B as result
 */
case class ContWrite[A,B](f: ContWrite[A,B] => Future[(Option[A], Continuated[B])], ch: Output[A]) extends Continuated[B]
{
  type El = A
}

/**
 * skip (i.e. do 'empty operation')
 */
case class Skip[A](f: Skip[A] => Future[Continuated[A]]) extends Continuated[A]


/**
 * never means the end of conversation
 */
case object Never extends Continuated[Nothing]



