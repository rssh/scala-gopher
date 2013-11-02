package gopher.channels

import scala.concurrent._

trait ChannelAction


trait ReadAction[-A] extends ChannelAction
   with (ReadActionInput[A] => Option[Future[ReadActionOutput]])
  
case class ReadActionInput[+A]( tie: TieReadJoin[A] , channel: InputChannel[A], value: A)
case class ReadActionOutput(continue: Boolean)

object ReadAction
{
  
  def apply[A](f: ReadActionInput[A] => Option[Future[ReadActionOutput]]):ReadAction[A] =
    new ReadAction[A]{ 
      override def apply(in:ReadActionInput[A]):Option[Future[ReadActionOutput]] = 
         f(in)  
   }
  
  
}

trait PlainReadAction[-A] extends ReadAction[A]
{
  
  override def apply(in: ReadActionInput[A]):Option[Future[ReadActionOutput]] =
    Some(Promise.successful(ReadActionOutput(plainApply(in))).future)
    
  def plainApply(in: ReadActionInput[A]): Boolean  

}

object PlainReadAction
{

   def apply[A](f: ReadActionInput[A] => Boolean):ReadAction[A] =
    new PlainReadAction[A]{ 
      override def plainApply(in:ReadActionInput[A]):Boolean = f(in)  
    }


}

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
