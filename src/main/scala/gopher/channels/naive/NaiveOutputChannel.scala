package gopher.channels.naive

import gopher.channels._
import scala.concurrent._
import akka.actor._

trait NaiveOutputChannel[-A] extends OutputChannel[A] with Activable {

  def addWriteListener(tie: NaiveTie, f: () => Future[Option[A]] )(implicit ec: ExecutionContext): Unit =
  {
    addWriteListener(tie, new WriteAction[A]{
      def apply(input:WriteActionInput[A]) = 
          Some(f() map (x => WriteActionOutput[A](x,true)))
    })
  }
  
  def addWriteListener(tie: NaiveTie,
                       f: WriteAction[A@scala.annotation.unchecked.uncheckedVariance] ): Unit


  
  def bindWrite(name: String)(implicit as: ActorSystem): ActorRef // =
  //{
  //  FromActorToChannel.create(write, name);
 // }
  
  
  
}