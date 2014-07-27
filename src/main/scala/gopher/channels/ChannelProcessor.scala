package gopher.channels

import akka.actor._

class ChannelProcessor extends Actor
{

   def receive = {
      case Done(r) => /* do nothing */
      case Skip(f) => ??? /* f map (x => self tell x) */
      case ContRead(f) => ???
      case Never => ???
   }


}
