package gopher.channels

import akka.actor._
import scala.language._
import scala.concurrent._
import scala.collection.immutable._
import gopher._

class ChannelOverflowException extends RuntimeException

/**
 * ChannelActor - actor, which leave
 */
class GrowingBufferedChannelActor[A](id:Long, limit:Int, api: GopherAPI) extends BaseBufferedChannelActor[A](id,api)
{


  protected[this] def onContWrite(cwa: gopher.channels.ContWrite[A, _]): Unit = 
  {
            if (closed) {
               cwa.flowTermination.throwIfNotCompleted(new ChannelClosedException())
            } else {
               val prevNElements = nElements
               if (processWriter(cwa) && prevNElements==0) {
                 processReaders()
               }
            }
  }

  protected[this] def onContRead(cra: gopher.channels.ContRead[A, _]): Unit =
  {
            if (nElements==0) {
               if (closed) {
                 processReaderClosed(cra)
               } else {
                 readers = readers :+ cra
               }
            } else {
               val prevNElements = nElements
               if (processReader(cra)) {
                 if (closed) {
                    stopIfEmpty
                 }
               }
            }
   }


  protected[this] def processReader[B](reader:ContRead[A,B]): Boolean =
   reader.function(reader) match {
       case Some(f1) => 
              val readedElement = buffer.head.asInstanceOf[A]
              buffer = buffer.tail
              nElements-=1
              Future{
                val cont = f1(ContRead.In value readedElement )
                api.continue(cont, reader.flowTermination)
              }(api.gopherExecutionContext)
              true
       case None =>
              false
   }


  private[this] def processWriter[B](writer:ContWrite[A,B]): Boolean =
   writer.function(writer) match {
       case Some((a,cont)) =>
                nElements+=1
                buffer = buffer :+ a
                api.continue(cont, writer.flowTermination)
                true
       case None => 
                false
   }


  var buffer= Queue[Any]()

}
