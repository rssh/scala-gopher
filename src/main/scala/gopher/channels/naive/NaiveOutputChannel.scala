package gopher.channels.naive

import gopher.channels._
import akka.actor._

trait NaiveOutputChannel[-A] extends OutputChannel[A] {

  def addWriteListener(tie: NaiveTie, f: () => Option[A] ): Unit =
  {
    addWriteListener(tie, new WriteAction[A]{
      def apply(input:WriteActionInput[A]) = WriteActionOutput[A](f(),true)
    })
  }
  
  def addWriteListener(tie: NaiveTie,
                       f: WriteAction[A@scala.annotation.unchecked.uncheckedVariance] ): Unit


  
  def bindWrite(name: String)(implicit as: ActorSystem): ActorRef // =
  //{
  //  FromActorToChannel.create(write, name);
 // }
  
  
  
}