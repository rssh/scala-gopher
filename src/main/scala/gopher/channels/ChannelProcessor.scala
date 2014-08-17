package gopher.channels

import akka.actor._
import scala.concurrent._

class ChannelProcessor(api: GopherAPI) extends Actor
{

   def receive = {
      case Done(r,ft) => ft.doExit(r)
      case sk@Skip(f,ft) =>  try {
                               f(sk).foreach(cont(_))
                             }catch{
                                case ex: Throwable => ft.doThrow(ex)
                             }
      case cr@ContRead(f,ch, ft) => ch.aread[cr.R]( (x,s) => f(x,s), ft )
      case cw@ContWrite(f,ch, ft) => ch.awrite[cw.R]( f , ft)
      case Never => /* do nothing */
   }

   def cont(x:Future[Continuated[_]]):Unit = x.foreach(self.tell(_,self))

   implicit val ec: ExecutionContext = api.executionContext

}
