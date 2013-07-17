package gopher

import scala.reflect._
import scala.concurrent._
import akka.actor._

package object channels {

  def make[A: ClassTag](capacity:  Int = 1000)(implicit ec: ExecutionContext): InputOutputChannel[A] = 
    {
      val retval = new GBlockedQueue[A](capacity,ec);
      //retval.process(executionContext);
      retval;
    }
  
  def bindRead[A](read: InputChannel[A], actor: ActorRef): Unit =
  {
    read.addListener( a => { actor ! a; true })
  }
  
  def bindWrite[A: ClassTag](write: OutputChannel[A], name: String)(implicit as: ActorSystem): ActorRef =
  {
    FromActorToChannel.create(write, name);
  }
  
  
}