package gopher.channels

import akka.actor._
import scala.concurrent._

class ChannelProcessor extends Actor
{

   def receive = {
      case Done(r) => /* do nothing */
      case sk@Skip(f) =>  f(sk).foreach(cont(_))
      case cr@ContRead(f,ch) => cont(ch.aread[cr.R]( (x,s) => f(x,s) ))
      case ContWrite(f,ch) => ???
      case Never => ???
   }

   def cont(x:Future[Continuated[_]]):Unit = x.foreach(self.tell(_,self))

   implicit val ec: ExecutionContext = ???

}
