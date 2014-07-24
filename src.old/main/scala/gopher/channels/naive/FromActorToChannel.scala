package gopher.channels.naive

import scala.concurrent._
import scala.reflect._
import akka._
import akka.actor._
import gopher.channels.OutputChannelBase



class FromActorToChannel[A](out: OutputChannelBase[A], atag:ClassTag[A]) extends Actor
{

   implicit val iatag = atag
   //implicit val iec = ec
  
   def receive =
   {
     case x:A => out.writeAsync(x)
   }
  
  
}


object FromActorToChannel
{
  
 def create[A:ClassTag](channel: OutputChannelBase[A], name: String)(implicit as: ActorSystem): ActorRef =
 {
   val props = Props(classOf[FromActorToChannel[A]],channel,implicitly[ClassTag[A]])
   as.actorOf(props, name)
 }

 
}

