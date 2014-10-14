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
 * this message is send, when all references to 
 * some instance of this channel are unreachable, 
 * so if we have no other instances (i.e. remote 
 * channel incarnation), than we must destroy channel.
 **/
case object ChannelRefDecrement

/**
 * this message is send, when we create new remote 
 * reference to channel, backed by this actor.
 **/
case object ChannelRefIncrement

/**
 * result of CloseChannelRead, return number of elements
 * left to read
 */
case class ChannelCloseProcessed(nElements: Int)


class ChannelActor[A](id:Long, capacity:Int, api: GopherAPI) extends Actor
{

  def receive = {
    case cw@ContWrite(_,_,ft) =>
            val cwa = cw.asInstanceOf[ContWrite[A,_]]
            if (closed) {
               ft.throwIfNotCompleted(new ChannelClosedException())
            } else {
              if (nElements==capacity) {
               writers = writers :+ cwa
              } else {
               val prevNElements = nElements
               if (processWriter(cwa) && prevNElements==0) {
                 processReaders()
               }
              }
            }
    case cr@ContRead(_,_,ft) =>
            val cra = cr.asInstanceOf[ContRead[A,_]]
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
     case ChannelRefDecrement =>
            nRefs -= 1
            if (nRefs == 0) {
               stopAll
            }
     case ChannelRefIncrement =>
            nRefs += 1
            
  }

  def processReaders() : Boolean =
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
              val cont = f1(ContRead.In value elementAt(readIndex) )
              nElements-=1
              readIndex+=1
              readIndex%=capacity
              api.continue(cont, reader.flowTermination)
              true
       case None =>
              false
   }

  private[this] def processReaderClosed[B](reader:ContRead[A,B]): Boolean =
   reader.function(reader) match {
       case Some(f1) => api.continue(f1(ContRead.ChannelClosed), reader.flowTermination)
                        true
       case None => false
   }

  def checkWriters: Boolean =
  {
    var retval = false
    while(!writers.isEmpty && nElements < capacity) {
      val current = writers.head
      writers = writers.tail
      val processed = processWriter(current)
      retval ||= processed
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
                api.continue(cont, writer.flowTermination)
                true
       case None => 
                false
   }


  private[this] def stopIfEmpty: Boolean =
  {
   require(closed==true)
   if (nElements == 0) {
      while(!readers.isEmpty) {
        val reader = readers.head
        val c = reader.asInstanceOf[ContRead[A,reader.R]]
        readers = readers.tail
        c.function(c) foreach { f1 =>
            api.continue(f1(ContRead.ChannelClosed), c.flowTermination)
        }
      }
   } 
   while(!writers.isEmpty) {
      val writer = writers.head
      val c = writer.asInstanceOf[ContWrite[A,writer.R]]
      writers = writers.tail
      c.function(c) foreach {
         f1 => c.flowTermination.throwIfNotCompleted(new ChannelClosedException())
      }
   }
   if (nElements == 0) {
      context.stop(self)
      true
   } else 
      false
  }

  def stopAll: Unit =
  {
    if (!closed) {
       closed=true
    } 
    if (!stopIfEmpty) {
       // stop anyway
       context.stop(self)
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

  var nRefs = 1

}
