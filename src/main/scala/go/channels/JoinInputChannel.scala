package go.channels

import java.util.concurrent._
import java.util.concurrent.locks._
import scala.concurrent.duration._


class JoinInputChannel[A](channels: IndexedSeq[InputChannel[A]])
{

   channels foreach {
      _.addListener(listener);
   }

   val listener = { (a:A) =>
     if (readLock.isLocked) {
         // i.e. we have readers.
         if (valueLock.tryLock()) {
            try {
              if (value==None) {
                value = Some(a)
                valueCondition.signal();
                true
              } else false
            } finally {
              valueLock.unlock();
            }
         } else false
     } else false
   }
        
   def readBlocked: A = 
   {
     readLock.lock();
     valueCondition.signal();
     try {
       var retval: Option[A] = None;
       while(retval==None) {
            valueLock.lock()
            try {
              if (value != None) {
                retval = value
                value = None
              } else {
                valueCondition.await();
              }
            } finally {
              valueLock.unlock();
            }
            if (retval != None) {
              valueCondition.signal();
            }
       }
       retval.get
     } finally {
       readLock.unlock();
     }
   }
     
   def readTimeout(timeout:Duration) : Option[A] = 
   {
    val endOfLock = System.currentTimeMillis() + timeout.unit.toMillis(timeout.length)
    if (readLock.tryLock(timeout.length, timeout.unit) ) {
       valueCondition.signal();
       try {
         var retval: Option[A] = None;
         while(retval==None && System.currentTimeMillis < endOfLock) {
            if (valueLock.tryLock(timeout.length, timeout.unit)) {
              try {
                if (value != None) {
                   retval = value
                   value = None
                } else {
                   val millisToLeft = endOfLock - System.currentTimeMillis
                   if (millisToLeft > 0) {
                      valueCondition.await(millisToLeft, TimeUnit.MILLISECONDS)
                   }
                }
              } finally {
                valueLock.unlock()
              }
            } 
            if (retval != None) {
              valueCondition.signal()
            }
         }
         retval
       } finally {
         readLock.unlock();
       } 
    } else None
   }

   private val readImmediatly: Option[A] =
   {
    var r:Option[A] = None
    channels.find{ ch => r=ch.readImmediatly;
                         r.isDefined }
    r
   }

   // locked when we have resource, waiting for event
   private val readLock = new ReentrantLock();

   // locked, when we do some operation with value.
   private val valueLock = new ReentrantLock();
   private val valueCondition = valueLock.newCondition();

   @volatile
   private var value: Option[A] = None

}


