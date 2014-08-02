package gopher.channels

import akka.actor._
import scala.concurrent._

class ChannelProcessor extends Actor
{

   def receive = {
      case Done(r) => /* do nothing */
      case sk@Skip(f) =>  f(sk).foreach(x=>x.foreach(cont(_)))
      case cr@ContRead(f,ch) => ch.aread[cr.R]( (x,s) => f(x,s) )
      case ContWrite(f,ch) => ???
      case Never => ???
   }

   def cont(x:Continuated[_]):Unit = self.tell(x,self)

   implicit val ec: ExecutionContext = ???

}
