package gopher.channels

import java.util.concurrent._
import java.util.concurrent.locks._
import scala.concurrent.duration.Duration


trait JLockHelper {

  @inline
  def inLock[A](lock:Lock)(f: =>A): A =
  {
    lock.lock();
    try {
      f
    } finally {
      lock.unlock();
    }
  }
  
  @inline
  def inTryLock[A](lock:Lock)(f: =>A, whenLocked: =>A): A =
  {
    if (lock.tryLock()) {
      try {
        f
      } finally {
        lock.unlock();
      }
    } else {
     whenLocked
    }
  }
  
  @inline 
  def inTryLock[A](lock:Lock, timeout: Duration)(f: =>A, whenLocked: =>A): A =
    if (lock.tryLock(timeout.length, timeout.unit)) {
       f 
    } else 
      whenLocked
  
  
}