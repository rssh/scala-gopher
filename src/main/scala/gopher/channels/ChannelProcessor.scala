package gopher.channels

import akka.actor._
import scala.concurrent._
import gopher._

class ChannelProcessor(api: GopherAPI) extends Actor
{

   def receive = {
      case Done(r,ft) => if (!ft.isCompleted) {
                            ft.doExit(r)
                         }
      case sk@Skip(f,ft) =>  if (!ft.isCompleted)  {
                              try{
                               f(sk) match {
                                 case Some(cont) => {
                                   val nowSender = sender
                                   cont.foreach( nowSender ! _ )
                                 }
                                 case None => /* do nothing */
                               }
                              }catch{
                                case ex: Throwable => ft.doThrow(ex)
                              }
                             }
      case cr@ContRead(f,ch, ft) => if (!ft.isCompleted) {
                                       ch.cbread[cr.R]( f, ft )
                                    }
      case cw@ContWrite(f,ch, ft) => if (!ft.isCompleted) {
                                       ch.cbwrite[cw.R]( f , ft)
                                     }
      case Never => /* do nothing */
   }

   def cont(x:Future[Continuated[_]]):Unit = x.foreach(self.tell(_,self))

   implicit val ec: ExecutionContext = api.executionContext

}
