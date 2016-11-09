package gopher.channels

import akka.actor._
import scala.language._
import scala.concurrent._
import scala.collection.immutable._
import gopher._


/**
 * ChannelActor - actor, which leave
 */
abstract class BaseBufferedChannelActor[A](id:Long, api: GopherAPI) extends ChannelActor[A](id,api)
{

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

  def stopIfEmpty: Boolean =
  {
   require(closed==true)
   if (nElements == 0) {
      stopReaders()
   }
   stopWriters()
   if (nElements == 0) {
      if (nRefs == 0) {
        // here we leave 'closed' channels in actor-system untile they will be
        // garbage-collected.  TODO: think about actual stop ?
        self ! GracefullChannelStop
      }
      true
   } else
      false
  }

  protected[this] def processReader[B](reader:ContRead[A,B]): Boolean 
 

  protected[this] def getNElements(): Int = nElements

  protected[this] var nElements=0

}
