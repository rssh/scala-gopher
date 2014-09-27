package gopher.channels

import akka.actor._
import scala.concurrent._
import scala.collection.immutable._
import gopher._

case object ChannelClose

/**
 * this is message wich send to ChannelActor, when we 
 * know, that channel is closed. In such case, we don't
 * konw: is actor stopped or not, So, we say this message
 * (instead read) and wait for reply. If reply is not received
 * within given timeout: think that channel is-dead.
 */
case class ClosedChannelRead(cont: ContRead[_,_])

/**
 * result of CloseChannelRead, return number of elements
 * left to read
 */
case class ChannelCloseProcessed(nElements: Integer)


class ChannelActor[A](id:Long, capacity:Int, api: GopherAPI) extends Actor
{

  def receive = {
    case cw@ContWrite(f,_,_) =>
            val cwa = cw.asInstanceOf[ContWrite[A,_]]
            if (nElements==capacity) {
               writers = writers :+ cwa
            } else {
               val prevNElements = nElements
               if (processWriter(cwa) && prevNElements==0) {
                 processReaders
               }
            }
    case cr@ContRead(_,_,ft) =>
            val cra = cr.asInstanceOf[ContRead[A,_]]
            if (nElements==0) {
               if (closed) {
                 ft.throwIfNotCompleted(new ChannelClosedException())
               } else {
                 readers = readers :+ cra
               }
            } else {
               val prevNElements = nElements
               if (processReader(cra)) {
                 if (closed) {
                    stopIfEmpty
                 } else if (prevNElements==capacity) {
                    checkWriters
                 }
               }
            }
     case ccr@ClosedChannelRead(_) =>
            self ! ccr.cont
            sender ! ChannelCloseProcessed(nElements)
     case ChannelClose =>
            closed=true
            stopIfEmpty
  }

  def processReaders: Boolean =
  {
    var retval = false
    while(!readers.isEmpty && nElements > 0) {
      val current = readers.head
      readers = readers.tail
      retval ||= processReader(current)
    }
    retval
  }

  private[this] def processReader[B](reader:ContRead[A,B]): Boolean =
   reader.function(reader) match {
       case Some(f1) => 
              val cont = f1(() => elementAt(readIndex))
              nElements-=1
              readIndex+=1
              readIndex%=capacity
              cont foreach ( api.continuatedProcessorRef ! _ )
              true
       case None =>
              false
   }

  def checkWriters: Boolean =
  {
    var retval = false
    while(!writers.isEmpty && nElements < capacity) {
      val current = writers.head
      writers = writers.tail
      val processed = processWriter(current)
      retval ||= processed
      if (!processed) {
        // TODO: add current to next-writers
      }
    }
    retval
  }

  private[this] def processWriter[B](writer:ContWrite[A,B]): Boolean =
   writer.function(writer) match {
       case Some((a,cont)) =>
                nElements+=1
                setElementAt(writeIndex,a)
                writeIndex+=1
                writeIndex%=capacity
                cont foreach ( api.continuatedProcessorRef ! _ )
                true
       case None => 
                false
   }


  private[this] def stopIfEmpty:Unit =
  {
   require(closed==true)
   if (nElements == 0) {
      while(!readers.isEmpty) {
        val reader = readers.head
        val c = reader.asInstanceOf[ContRead[A,reader.R]]
        readers = readers.tail
        //val a: A = _
        c.function(c) foreach {
          f1 => c.flowTermination.throwIfNotCompleted(new ChannelClosedException())
        }
      }
   }
  }

  private[this] implicit def ec: ExecutionContext = api.executionContext

  @inline
  private[this] def elementAt(i:Int): A =
    buffer(i).asInstanceOf[A]

  @inline
  private[this] def setElementAt(i:Int, a:A): Unit =
    buffer(i) = a.asInstanceOf[AnyRef]


  // boxed representation of type.
  val buffer= new Array[AnyRef](capacity)
  var readIndex=0
  var writeIndex=0
  var nElements=0
  var closed=false
  var readers = Queue[ContRead[A,_]] ()
  var writers = Queue[ContWrite[A,_]] ()

}
