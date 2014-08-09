package gopher.channels

import akka.actor._
import scala.concurrent._

class ChannelProcessor(api: API) extends Actor
{

   def receive = {
      case Done(r) => /* do nothing */
      case sk@Skip(f) =>  f(sk).foreach(cont(_))
      case cr@ContRead(f,ch) => ch.aread[cr.R]( (x,s) => f(x,s) )
      case cw@ContWrite(f,ch) => ch.awrite[cw.R]( f )
      case Never => /* do nothing */
   }

   def cont(x:Future[Continuated[_]]):Unit = x.foreach(self.tell(_,self))

   implicit val ec: ExecutionContext = api.executionContext

}
