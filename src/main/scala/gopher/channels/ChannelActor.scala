package gopher.channels

import akka.actor._
import scala.concurrent._
import scala.collection.immutable._
import gopher._

case object ChannelClose


class ChannelActor[A](id:Long, capacity:Int, api: GopherAPI) extends Actor
{

  // TODO: check case when f() fail [throw exception]
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
    case cr@ContRead(f,_,_) =>
            val cra = cr.asInstanceOf[ContRead[A,_]]
            if (nElements==0) {
               readers = readers :+ cra
            } else {
               val prevNElements = nElements
               if (processReader(cra) && prevNElements==capacity) {
                 checkWriters
               }
            }
     case ChannelClose =>
            // TODO: remove name from system.
            closed=true
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
   reader.f(elementAt(readIndex),reader) match {
       case Some(cont) => 
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
      retval ||= processWriter(current)
    }
    retval
  }

  private[this] def processWriter[B](writer:ContWrite[A,B]): Boolean =
   writer.f(writer) match {
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
