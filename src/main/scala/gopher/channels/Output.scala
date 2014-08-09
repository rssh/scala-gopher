package gopher.channels

import scala.concurrent._

/**
 * Entity, which can 'eat' objects of type A,
 * can be part of channel
 */
trait Output[A]
{

  /**
   * apply f and send result to channels processor.
   */
  def  awrite[B](f: ContWrite[A,B] => Option[(A,Future[Continuated[B]])]): Unit

  def  write(a:A):Future[Unit] =
  {
   val p = Promise[Unit]()
   awrite[Unit]( cont => {
            p success (())
            Some((a,Future.successful(Done(()))))
          }
         )
   p.future
  }
  

}
