package go.channels


import java.util.concurrent._
import java.util.concurrent.locks._
import java.lang.ref._
import scala.annotation.tailrec
import scala.reflect.ClassTag
import scala.concurrent.duration._


/**
 * classical blocked queue, which supports listeners.
 */
class GBlockedQueue[A: ClassTag](size: Int) extends InputChannel[A] with OutputChannel[A]
{

     // Members declared in InputChannel

     /**
      * called, when we want to deque object to readed.
      * If listener accepts read, it returns true with given object,
      * and we delete listener from queue.
      */
      def addReadListener(f: A => Boolean): Unit = 
      {
        readListenersLock.lock()
        try {
          readListeners = (new WeakReference(f))::readListeners
        } finally {
          readListenersLock.unlock()
        }
      }

      def addListener(f: A => Boolean): Unit = addReadListener(f)

      def readBlocked: A = 
      { 
        // todo: thinking about creation of temporary listener here.
        bufferLock.lock();
        try {
          while(count == 0) {
             readPossibeCondition.await()
          }
          val retval = buffer(readIndex)
          freeElementBlocked
          retval
        } finally {
          bufferLock.unlock();
        }
      }

      def readImmediatly: Option[A] = 
       if (bufferLock.tryLock) {
         try {
           if (count > 0) {
             val retval = buffer(readIndex)
             freeElementBlocked
             Some(retval)
           } else None
         } finally {
           bufferLock.unlock()
         }
       } else None

      // giess that we work in millis resolution.
      def readTimeout(timeout: Duration): Option[A] = 
      {
       val endOfLock = System.currentTimeMillis() + timeout.unit.toMillis(timeout.length)
       if (bufferLock.tryLock(timeout.length,timeout.unit)) {
         try {
            var millisToLeft = endOfLock - System.currentTimeMillis()
            while(count == 0 && millisToLeft > 0) {
               readPossibeCondition.await(millisToLeft, TimeUnit.MILLISECONDS)
               millisToLeft = endOfLock - System.currentTimeMillis()
            }
            if (count > 0) {
              val retval = buffer(readIndex)
              freeElementBlocked
              Some(retval)
            } else None
         } finally {
            bufferLock.unlock();
         }
       } else None
      }

      // Members declared in go.OutputChannel
      def addListener(f: () => Option[A]): Unit = 
      {
        writeListenersLock.lock()
        try {
          writeListeners = (new WeakReference(f))::writeListeners
        } finally {
          writeListenersLock.unlock()
        }
      }

      def writeBlocked(x: A): Unit = 
      {
        bufferLock.lock();
        try {
          while(count == size) {
             writePossibeCondition.await()
          }
          writeElementBlocked(x)
        } finally {
          bufferLock.unlock();
        }
      }

      def writeImmediatly(x: A): Boolean = 
       if (bufferLock.tryLock) {
         try {
             if (count < size) {
               writeElementBlocked(x)
               true
             } else false
         } finally {
             bufferLock.unlock();
         }
       } else false
      

      def writeTimeout(x: A,timeout: Duration): Boolean = 
      {
       val endOfLock = System.currentTimeMillis() + timeout.unit.toMillis(timeout.length)
       if (bufferLock.tryLock(timeout.length,timeout.unit)) {
         try {
            var millisToLeft = endOfLock - System.currentTimeMillis()
            while( count == size && millisToLeft > 0) {
               writePossibeCondition.await(millisToLeft, TimeUnit.MILLISECONDS)
               millisToLeft = endOfLock - System.currentTimeMillis()
            }
            if (count < size) {
              writeElementBlocked(x)
              true
            } else false
         } finally {
            bufferLock.unlock();
         }
       } else false
      }



  @tailrec
  private def newElementInternalListeners
  {
    if (fireNewElementBlocked) {
      if (fireNewSpaceBlocked) {
        newElementInternalListeners
      } else {
        writePossibeCondition.signal()
      }
    } else {
      readPossibeCondition.signal()
    }
  }
  
  private def fireNewSpaceBlocked: Boolean =
  {
   var c = writeListeners
   var nNulls = 0;
   var retval = false;
   while(c!=Nil) {
     val h=c.head
     c = c.tail
     val writeListener = h.get
     if ( !(writeListener eq null)) {
        writeListener.apply() match {
          case None =>
          case Some(a) => writeElementBlocked(a)
                          retval = true
                          c = Nil
        }
     } else {
        nNulls += 1
     }
   }
   if (nNulls > 1) {
     cleanupWriteListenersRefs
   }
   retval
  }

  private def fireNewElementBlocked: Boolean =
  {
   var c = readListeners
   var nNulls = 0;
   var retval = false;
   while(c!=Nil) {
     val h=c.head
     c = c.tail
     val readListener = h.get
     if (!(readListener eq null)) {
       if (readListener.apply(buffer(readIndex))) {
           freeElementBlocked
           retval=true
           c=Nil
       }
     } else {
       nNulls = nNulls + 1
     }
   }
   // TODO: - move when unblocked.
   if (nNulls > 0) {
     cleanupReadListenersRefs
   }
   retval;
  }

  private def freeElementBlocked =
  {
   buffer(readIndex) = emptyA
   readIndex = ((readIndex+1) % size)
   count -= 1
  }

  private def writeElementBlocked(a:A) =
  {
   buffer(writeIndex)=a
   writeIndex = ((writeIndex+1) % size)
   count += 1
  }

  private def cleanupReadListenersRefs: Unit =
  {
   readListenersLock.lock()
   try {
     readListeners = readListeners.filter(_.get eq null)
   } finally {
     readListenersLock.unlock()
   }
  }

  private def cleanupWriteListenersRefs: Unit =
  {
   writeListenersLock.lock()
   try {
     writeListeners = writeListeners.filter(_.get eq null)
   } finally {
     writeListenersLock.unlock()
   }
  }

  class Async extends InputAsync with OutputAsync
  {}

  override def async = new Async { }



  private[this] val buffer: Array[A] = new Array[A](size)

  @volatile
  private[this] var readIndex: Int = 0;
  @volatile
  private[this] var writeIndex: Int = 0;
  @volatile
  private[this] var count: Int = 0;

  private[this] val bufferLock = new ReentrantLock();
  private[this] val readPossibeCondition = bufferLock.newCondition
  private[this] val writePossibeCondition = bufferLock.newCondition

  private[this] var readListenersLock = new ReentrantLock();
  private[this] var readListeners: List[WeakReference[A => Boolean]] = Nil

  private[this] var writeListenersLock = new ReentrantLock();
  private[this] var writeListeners: List[WeakReference[()=>Option[A]]] = Nil

  private[this] final var emptyA: A = _

}
