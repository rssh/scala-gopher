package gopher.channels

import scala.concurrent._

/**
 * Entity, which can 'eat' objects of type A,
 * can be part of channel
 */
trait Output[A]
{

  def  awrite[B](f: ContWrite[A,B] => Option[Future[(A,Continuated[B])]]): Future[Continuated[B]] 

  def  write(a:A):Future[Unit] =
  {
   val p = Promise[Unit]()
   awrite[Unit]( cont => {
            p success (())
            Some(Future.successful((a,Done(()))))
          }
         )
   p.future
  }
  

}
