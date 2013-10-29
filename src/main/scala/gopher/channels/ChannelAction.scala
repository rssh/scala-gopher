package gopher.channels

import scala.concurrent._

trait ChannelAction


trait ReadAction[-A] extends ChannelAction
   with (ReadActionInput[A] => Option[Future[ReadActionOutput]])
  
case class ReadActionInput[+A]( tie: TieReadJoin[A] , channel: InputChannel[A], value: A)
case class ReadActionOutput(continue: Boolean)
  

trait WriteAction[A] extends ChannelAction
  with (WriteActionInput[A] => Option[Future[WriteActionOutput[A]]])
  
case class WriteActionInput[A](tie: TieWriteJoin[A], channel: OutputChannel[A])
case class WriteActionOutput[A](value: Option[A], continue: Boolean)
    
trait IdleAction extends ChannelAction
    with (TieJoin => Future[Boolean])

  
object IdleAction 
{

   val doNothing: IdleAction = new IdleAction{
     def apply(tie: TieJoin): Future[Boolean] = Promise.successful(true).future
   }

}
