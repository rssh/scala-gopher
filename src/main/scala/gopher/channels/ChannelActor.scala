package gopher.channels

import akka.actor._
import scala.language._
import scala.concurrent._
import scala.collection.immutable._
import gopher._


/**
 * ChannelActor - actor, which leave
 */
abstract class ChannelActor[A](id:Long, api: GopherAPI) extends Actor
{

  def receive = {
    case cw@ContWrite(_,_,ft) => 
            val cwa = cw.asInstanceOf[ContWrite[A,cw.R]]
            onContWrite(cwa)
    case cr@ContRead(_,_,ft) =>
            val cra = cr.asInstanceOf[ContRead[A,cr.R]]
            onContRead(cra)
    case ccr@ClosedChannelRead(_) =>
            self ! ccr.cont
            sender ! ChannelCloseProcessed(getNElements())
    case ChannelClose =>
            closed=true
            stopIfEmpty
    case ChannelRefDecrement =>
            nRefs -= 1
            if (nRefs == 0) {
               stopAll
            }
    case ChannelRefIncrement =>
            nRefs += 1
    case GracefullChannelStop =>
            context.stop(self)
  }

  protected[this] def onContWrite(cw:ContWrite[A,_]):Unit

  protected[this] def onContRead(cr:ContRead[A,_]):Unit

  protected[this] def getNElements():Int

  protected[this] def processReaderClosed[B](reader:ContRead[A,B]): Boolean =
   reader.function(reader) match {
       case Some(f1) => api.continue(f1(ContRead.ChannelClosed), reader.flowTermination)
                        true
       case None => false
   }

  protected[this] def stopReaders(): Unit =
  {
      while(!readers.isEmpty) {
        val reader = readers.head
        val c = reader.asInstanceOf[ContRead[A,reader.R]]
        readers = readers.tail
        c.function(c) foreach { f1 =>
            api.continue(f1(ContRead.ChannelClosed), c.flowTermination)
        }
      }
  }

  protected[this] def stopWriters(): Unit =
  {
   while(!writers.isEmpty) {
      val writer = writers.head
      val c = writer.asInstanceOf[ContWrite[A,writer.R]]
      writers = writers.tail
      c.function(c) foreach {
         f1 => c.flowTermination.throwIfNotCompleted(new ChannelClosedException())
      }
   }
  }

  def stopIfEmpty: Boolean

  def stopAll: Unit =
  {
    if (!closed) {
       closed=true
    } 
    if (!stopIfEmpty) {
       // stop anyway
       self ! GracefullChannelStop
    }
  }

  protected[this] implicit def ec: ExecutionContext = api.executionContext

  protected[this] var closed=false
  var readers = Queue[ContRead[A,_]] ()
  var writers = Queue[ContWrite[A,_]] ()
  var nRefs = 1

}
