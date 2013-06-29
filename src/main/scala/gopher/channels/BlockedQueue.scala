package gopher.channels

import java.util.concurrent.{ Future => JFuture, _ }
import java.util.concurrent.locks._
import java.lang.ref._
import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.reflect._
import scala.concurrent._

/**
 * classical blocked queue, which supports listeners.
 */
class GBlockedQueue[A: ClassTag](size: Int, ec: ExecutionContext) extends InputOutputChannel[A] with JLockHelper {

  /**
   * called, when we want to deque object to readed.
   * If listener accepts read, it returns true with given object.
   * Queue holds weak referencde to listener, so we stop sending
   * message to one, when listener is finalized.
   */
  def addReadListener(f: A => Boolean): Unit =
    {
      inLock(readListenersLock) {
        readListeners = (new WeakReference(f)) :: readListeners
      }
      tryDoStepAsync();
    }

  def addListener(f: A => Boolean): Unit = addReadListener(f)

  def readBlocked: A =
    {
      if (shutdowned) {
        throw new IllegalStateException("quue is shutdowned")
      }
      val retval = inLock(bufferLock) {
        while (count == 0) {
          readPossibleCondition.await()
        }
        val retval = buffer(readIndex)
        freeElementBlocked
        retval
      }
      tryDoStepAsync();
      retval
    }

  def readImmediatly: Option[A] =
    optTryDoStepAsync(inTryLock(bufferLock)(readElementBlocked, None))

  // guess that we work in millis resolution.
  def readTimeout(timeout: Duration): Option[A] =
    optTryDoStepAsync {
      val endOfLock = System.currentTimeMillis() + timeout.unit.toMillis(timeout.length)
      inTryLock(bufferLock, timeout)({
        var millisToLeft = endOfLock - System.currentTimeMillis()
        while (count == 0 && millisToLeft > 0) {
          readPossibleCondition.await(millisToLeft, TimeUnit.MILLISECONDS)
          millisToLeft = endOfLock - System.currentTimeMillis()
        }
        readElementBlocked
      }, None)
    }

  // Members declared in go.OutputChannel
  def addListener(f: () => Option[A]): Unit =
    {
      inLock(writeListenersLock) {
        writeListeners = (new WeakReference(f)) :: writeListeners
      }
      tryDoStepAsync()
    }

  def writeBlocked(x: A): Unit =
    {
      val retval = inLock(bufferLock) {
        var writed = false
        while (!writed) {
          while (count == size) {
            writePossibleCondition.await()
          }
          writed = writeElementBlocked(x)
        }
        readPossibleCondition.signal()
      }
      tryDoStepAsync
      retval
    }

  def writeImmediatly(x: A): Boolean =
    condTryDoStepAsync(
      inTryLock(bufferLock)(
        writeElementBlocked(x), false))

  def writeTimeout(x: A, timeout: Duration): Boolean =
    condTryDoStepAsync {
      val endOfLock = System.currentTimeMillis() + timeout.unit.toMillis(timeout.length)
      inTryLock(bufferLock, timeout)({
        var millisToLeft = endOfLock - System.currentTimeMillis()
        while (count == size && millisToLeft > 0) {
          writePossibleCondition.await(millisToLeft, TimeUnit.MILLISECONDS)
          millisToLeft = endOfLock - System.currentTimeMillis()
        }
        writeElementBlocked(x)
      }, false)
    }

  def shutdown() {
    shutdowned = true;
  }

  @inline
  private[this] def condTryDoStepAsync(x: Boolean): Boolean =
    {
      if (x) tryDoStepAsync()
      x
    }

  @inline
  private[this] def optTryDoStepAsync[T](x: Option[T]) =
    {
      if (x.isDefined) {
        tryDoStepAsync()
      }
      x
    }

  private[this] def tryDoStepAsync() =
    inTryLock(bufferLock)(doStepAsync(), ())

  def activate() = tryDoStepAsync  
    
  /**
   * Run chunk of queue event loop inside thread, specified by
   * execution context, passed in channel initializer;
   *
   */
  def doStepAsync() {
    implicit val ec = executionContext;
    Future {
      val wasContinue = doStep();
      if (wasContinue && !shutdowned) {
        doStepAsync()
      }
    }
  }

  /**
   * Run chunk of queue event loop inside current thread.
   *
   */
  def doStep(maxN: Int = 10000): Boolean =
    inLock(bufferLock) {
      var toContinue = true
      var wasContinue = false;
      var n = maxN;
      val prevCount = count;
      while (toContinue) {
        val readAction = (count > 0 && fireNewElementBlocked)
        val writeAction = (count < size && fireNewSpaceBlocked)
        if (prevCount == size && count < size) {
          writePossibleCondition.signal()
        } else if (prevCount == 0 && count > 0) {
          readPossibleCondition.signal()
        }
        wasContinue = (readAction || writeAction);
        toContinue ||= wasContinue
        n = n - 1
        toContinue &&= (n > 0)
      }
      wasContinue
    }

  private def fireNewSpaceBlocked: Boolean =
    {
      var c = writeListeners
      var nNulls = 0;
      var retval = false;
      while (c != Nil) {
        val h = c.head
        c = c.tail
        val writeListener = h.get
        if (!(writeListener eq null)) {
          writeListener.apply() match {
            case None =>
            case Some(a) =>
              writeElementBlocked(a)
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
      while (c != Nil) {
        val h = c.head
        c = c.tail
        val readListener = h.get
        if (!(readListener eq null)) {
          if (readListener.apply(buffer(readIndex))) {
            freeElementBlocked
            retval = true
            c = Nil
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
      readIndex = ((readIndex + 1) % size)
      count -= 1
    }

  private def readElementBlocked: Option[A] =
    {
      if (count > 0) {
        val retval = buffer(readIndex)
        freeElementBlocked
        Some(retval)
      } else None
    }

  private def writeElementBlocked(a: A): Boolean =
    {
      if (count < size) {
        buffer(writeIndex) = a
        writeIndex = ((writeIndex + 1) % size)
        count += 1
        true
      } else {
        false
      }
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

  private[this] val buffer: Array[A] = new Array[A](size)

  @volatile
  private[this] var readIndex: Int = 0;
  @volatile
  private[this] var writeIndex: Int = 0;
  @volatile
  private[this] var count: Int = 0;
  @volatile
  private[this] var shutdowned: Boolean = false;

  private[this] val bufferLock = new ReentrantLock();
  private[this] val readPossibleCondition = bufferLock.newCondition
  private[this] val writePossibleCondition = bufferLock.newCondition

  private[this] var readListenersLock = new ReentrantLock();
  private[this] var readListeners: List[WeakReference[A => Boolean]] = Nil

  private[this] var writeListenersLock = new ReentrantLock();
  private[this] var writeListeners: List[WeakReference[() => Option[A]]] = Nil

  private[this] val executionContext: ExecutionContext = ec;

  private[this] final var emptyA: A = _

}
