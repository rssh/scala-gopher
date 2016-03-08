package gopher.channels

import akka.actor._
import scala.language._
import scala.concurrent._
import scala.collection.immutable._
import gopher._


/**
 * ChannelActor - actor, which leave
 */
class UnbufferedChannelActor[A](id:Long, api: GopherAPI) extends Actor
{

  def receive = {
    case cw@ContWrite(_,_,ft) =>
            val cwa = cw.asInstanceOf[ContWrite[A,_]]
            if (closed) {
               ft.throwIfNotCompleted(new ChannelClosedException())
            } else if (!processReaders(cwa))  {
               writers = writers :+ cwa
            }
    case cr@ContRead(_,_,ft) =>
            val cra = cr.asInstanceOf[ContRead[A,_]]
            if (closed) {
               processReaderClosed(cra)
            } else if (!processWriters(cra)) {
               readers = readers :+ cra;         
            }
     case ccr@ClosedChannelRead(_) =>
            self ! ccr.cont
            sender ! ChannelCloseProcessed(0)
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

  def processReaders(w: ContWrite[A,_]) : Boolean =
  {
    var done = false
    while(!(done || readers.isEmpty)) {
      val current = readers.head
      readers = readers.tail
      done = processReader(current,w)
    }
    done
  }

  private[this] def processReader[B,C](reader:ContRead[A,B],writer:ContWrite[A,C]): Boolean =
   reader.function(reader) match {
       case Some(f1) => 
              writer.function(writer) match {
                case Some((a,wcont)) =>
                      Future{
                        val cont = f1(ContRead.In value a)
                        api.continue(cont, reader.flowTermination)
                      }(api.executionContext)
                      api.continue(wcont, writer.flowTermination)
                      true
                case None =>
                      val cont = f1(ContRead.Skip)
                      api.continue(cont, reader.flowTermination)
                      false
              }
       case None =>
              false
   }

  private[this] def processReaderClosed[B](reader:ContRead[A,B]): Boolean =
   reader.function(reader) match {
       case Some(f1) => api.continue(f1(ContRead.ChannelClosed), reader.flowTermination)
                        true
       case None => false
   }

  def processWriters[C](reader:ContRead[A,C]): Boolean =
  {
    if (writers.isEmpty) {
      false
    } else {
      reader.function(reader) match {
         case Some(f1) =>
            var done = false
            while(!writers.isEmpty && !done) {
              val current = writers.head
              writers = writers.tail
              done = processWriter(current,f1,reader)
            }
            if (!done) {
              f1(ContRead.Skip)
            }
            done
         case None => true
      }
    }
  }

  private[this] def processWriter[B,C](writer:ContWrite[A,B],
                                       f1:ContRead.In[A]=>Future[Continuated[C]],
                                       reader:ContRead[A,C]): Boolean =
   writer.function(writer) match {
       case Some((a,wcont)) =>
                Future {
                  val rcont = f1(ContRead.In value a)
                  api.continue(rcont,reader.flowTermination)
                }
                api.continue(wcont,writer.flowTermination)
                true
       case None => 
                false
   }


  private[this] def stopIfEmpty: Boolean =
  {
   require(closed==true)
   while(!readers.isEmpty) {
      val reader = readers.head
      val c = reader.asInstanceOf[ContRead[A,reader.R]]
      readers = readers.tail
      c.function(c) foreach { f1 =>
            api.continue(f1(ContRead.ChannelClosed), c.flowTermination)
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
      if (nRefs == 0) {
        // here we leave 'closed' channels in actor-system untile they will be
        // garbage-collected.  TODO: think about actual stop ?
        self ! GracefullChannelStop
      }
      true
  }

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

  private[this] implicit def ec: ExecutionContext = api.executionContext

  var closed=false
  var readers = Queue[ContRead[A,_]] ()
  var writers = Queue[ContWrite[A,_]] ()
  var nRefs = 1

}
