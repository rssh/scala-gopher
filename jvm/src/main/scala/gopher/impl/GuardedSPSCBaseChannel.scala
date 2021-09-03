package gopher.impl

import cps._
import gopher._
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import scala.util.Try
import scala.util.Success
import scala.util.Failure

import java.util.logging.{Level => LogLevel}


/**
 * Guarded channel work in the next way:
 *   reader and writer asynchronically added to readers and writers and force evaluation of internal step function
 *    or ensure that currently running step function will see the chanes in readers/writers.
 *   Step functions is executed in some thread loop, and in the same time, only one instance of step function is running.
 *   (which is ensured by guard)
 **/
abstract class GuardedSPSCBaseChannel[F[_]:CpsAsyncMonad,A](override val gopherApi: JVMGopher[F], controlExecutor: ExecutorService, taskExecutor: ExecutorService) extends Channel[F,A,A]:

  import GuardedSPSCBaseChannel._

  protected val readers = new ConcurrentLinkedDeque[Reader[A]]()
  protected val writers = new ConcurrentLinkedDeque[Writer[A]]()
  protected val doneReaders = new ConcurrentLinkedDeque[Reader[Unit]]()

  protected val publishedClosed = new AtomicBoolean(false)

  protected val stepGuard = new AtomicInteger(STEP_FREE)

  protected val stepRunnable: Runnable = (()=>entryStep())
 
  def addReader(reader: Reader[A]): Unit =
    if (reader.canExpire) then
      readers.removeIf( _.isExpired )   
    // if (publishedClosed.get()) then
    //  tryClosedRead()
    // else 
    readers.add(reader)
    controlExecutor.submit(stepRunnable)

  def addWriter(writer: Writer[A]): Unit =
    if (writer.canExpire) then
      writers.removeIf( _.isExpired )
    if (publishedClosed.get()) then
      closeWriter(writer)
    else
      writers.add(writer)
      controlExecutor.submit(stepRunnable)

  def addDoneReader(reader: Reader[Unit]): Unit =
    if (reader.canExpire)
      doneReaders.removeIf( _.isExpired )
    if (publishedClosed.get()) then
      closeDoneReader(reader)
    else
      doneReaders.add(reader)
      controlExecutor.submit(stepRunnable)
      
  def close(): Unit =
    publishedClosed.set(true)
    controlExecutor.submit(stepRunnable)    

  def isClosed: Boolean =
    publishedClosed.get()

  protected def step(): Unit
  

  protected def entryStep(): Unit =
    var done = false
    var nSpins = 0
    while(!done) {
       if (stepGuard.compareAndSet(STEP_FREE,STEP_BUSY)) {
          done = true
          step()
       } else if (stepGuard.compareAndSet(STEP_BUSY, STEP_UPDATED)) {
          done = true
       } else if (stepGuard.get() == STEP_UPDATED) {
         // merge with othwer changes
          done = true
       } else {
         // other set updates, we should spinLock
         nSpins = nSpins + 1
         Thread.onSpinWait()
       }
    }

  /**
  * if truw - we can leave step, otherwise better run yet one step.
  */  
  protected def checkLeaveStep(): Boolean =
    if (stepGuard.compareAndSet(STEP_BUSY,STEP_FREE)) then
      true
    else if (stepGuard.compareAndSet(STEP_UPDATED, STEP_BUSY)) then
      false
    else
      // impossible, let'a r
      false

  // precondition: writers are empty
  protected def processReadClose(): Boolean  = 
    require(writers.isEmpty)
    var progress = false
    while(!readers.isEmpty) {
      val r = readers.poll()
      if (!(r eq null) && !r.isExpired) then
        r.capture() match
            case Expirable.Capture.Ready(f) =>
              progress = true
              //println("sending signal in processReadClose");
              //val prevEx = new RuntimeException("prev")
              taskExecutor.execute(() => {
                //try
                  //println(s"calling $f, channel = ${GuardedSPSCBaseChannel.this}")
                  // prevEx.printStackTrace()
                  val debugInfo = s"channel=${this}, writersEmpty=${writers.isEmpty}, readersEmpty=${readers.isEmpty}, r=$r, f=$f"
                  f(Failure(new ChannelClosedException(debugInfo))) 
                //catch
                //  case ex: Exception =>
                //    println(s"exception in close-reader, channel=${GuardedSPSCBaseChannel.this}, f=$f, r=$r")
                //    throw ex
              })
              r.markUsed()
            case Expirable.Capture.WaitChangeComplete =>
              progressWaitReader(r)
            case Expirable.Capture.Expired =>  
              progress = true
    }
    progress

  // TODO: remove.  If we have writers in queue, 
  protected def processWriteClose(): Boolean =
    var progress = false
    while(!writers.isEmpty) {
      val w = writers.poll()
      if !(w eq null) && !w.isExpired then
        w.capture() match
          case Expirable.Capture.Ready((a,f)) =>
            progress = true
            taskExecutor.execute(() => f(Failure(new ChannelClosedException)) )
            w.markUsed()
          case Expirable.Capture.WaitChangeComplete =>
            progressWaitWriter(w)
          case Expirable.Capture.Expired =>
            progress = true
    }
    progress
  

  protected def processDoneClose(): Boolean  = {
    var progress = false
    while(!doneReaders.isEmpty) {
      val r = doneReaders.poll()
      if !(r eq null) && !r.isExpired then
        r.capture() match
          case Expirable.Capture.Ready(f) =>
            progress = true
            taskExecutor.execute(() => f(Success(())))
            r.markUsed()  
          case Expirable.Capture.WaitChangeComplete =>
            progressWaitDoneReader(r)
          case Expirable.Capture.Expired =>
            progress = true
    }
    progress
  }
  
  protected def closeDoneReader(r: Reader[Unit]): Unit = {
    while 
      r.capture() match
        case Expirable.Capture.Ready(f) =>
          taskExecutor.execute(()=>f(Success(())))
          r.markUsed()
          false
        case Expirable.Capture.WaitChangeComplete =>
          progressWaitDoneReader(r)
          true
        case Expirable.Capture.Expired =>
          false
    do ()
  }

  protected def closeWriter(w: Writer[A]): Unit = {
    var done = false
    while (!done && !w.isExpired)
      w.capture() match
        case Expirable.Capture.Ready((a,f)) => 
          taskExecutor.execute(() => f(Failure(new ChannelClosedException)) )
          w.markUsed()
          done = true
        case Expirable.Capture.WaitChangeComplete =>
          Thread.onSpinWait()
        case Expirable.Capture.Expired =>
          done = true
  }



  // precondition:  r.capture() == None
  protected def progressWaitReader(r: Reader[A]): Unit =
    progressWait(r,readers)
  
  // precondition:  w.capture() == None
  protected def progressWaitWriter(w: Writer[A]): Unit =
    progressWait(w,writers)
  
  protected def progressWaitDoneReader(r: Reader[Unit]): Unit =
    progressWait(r,doneReaders)
      
  protected def progressWait[T <: Expirable[_]](v:T, queue: ConcurrentLinkedDeque[T]): Unit =
    if (!v.isExpired) 
      if (queue.isEmpty)
        Thread.onSpinWait()
        // if (nSpins > JVMGopher.MAX_SPINS)
        //   Thread.`yield`()
      queue.addLast(v)



object GuardedSPSCBaseChannel:

  final val STEP_FREE = 0

  final val STEP_BUSY = 1

  final val STEP_UPDATED = 2



