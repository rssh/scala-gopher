package gopher.channels

trait ChannelAction 

trait ReadAction[A] extends ChannelAction
   with (ReadActionInput[A] => ReadActionOutput)
  
case class ReadActionInput[A](tie: Tie, channel: InputChannel[A], value:A)
case class ReadActionOutput(readed: Boolean, continue: Boolean)
  

trait WriteAction[A] extends ChannelAction
  with (WriteActionInput[A] => WriteActionOutput[A] )
  
case class WriteActionInput[A](tie: Tie, channel: OutputChannel[A] )
case class WriteActionOutput[A](writed: Option[A], continue: Boolean)

trait IdleAction extends ChannelAction
    with (Tie => Boolean)

  
object IdleAction
{

   val doNothing: IdleAction = new IdleAction{
     def apply(tie: Tie) = true
   }

}
