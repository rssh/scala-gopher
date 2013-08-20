package gopher.channels.naive

import scala.concurrent._
import scala.reflect._
import akka._
import akka.actor._
import gopher.channels.OutputChannel


class FromActorToChannel[A](out: OutputChannel[A], atag:ClassTag[A]) extends Actor 
{
   implicit val iatag = atag
  
   def receive =
   {
     
     case x: A => out.writeBlocked(x);
   }
   
}

class FromActorToChannelAsync[A](out: OutputChannel[A], atag:ClassTag[A], ec: ExecutionContext) extends Actor
{

   implicit val iatag = atag
   implicit val iec = ec
  
   def receive =
   {
     case x:A => out.async.write(x)
   }
  
  
}


object FromActorToChannel
{
  
 def create[A:ClassTag](channel: OutputChannel[A], name: String)(implicit as: ActorSystem): ActorRef =
 {
   val props = Props(classOf[FromActorToChannel[A]],channel,implicitly[ClassTag[A]])
   as.actorOf(props, name)
 }

 def createAsyc[A:ClassTag](out: OutputChannel[A], name: String)(implicit as: ActorSystem, ec:ExecutionContext): ActorRef =
 {
   val props = Props(classOf[FromActorToChannelAsync[A]],out.async,implicitly[ClassTag[A]])
   as.actorOf(props, name)

 } 
 
}

