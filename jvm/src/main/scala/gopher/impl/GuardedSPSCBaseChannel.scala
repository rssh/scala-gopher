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

  
  protected def processReadClose(): Boolean  = 
    var progress = false
    while(!readers.isEmpty) {
      val r = readers.poll()
      if (!(r eq null) && !r.isExpired) then
        r.capture() match
            case Some(f) =>
              progress = true
              taskExecutor.execute(() => f(Failure(new ChannelClosedException())) )
              r.markUsed()
            case None =>
              progress = true
              progressWaitReader(r)
    }
    progress

  protected def processWriteClose(): Boolean =
    var progress = false
    while(!writers.isEmpty) {
      val w = writers.poll()
      if !(w eq null) && !w.isExpired then
        w.capture() match
          case Some((a,f)) =>
            progress = true
            taskExecutor.execute(() => f(Failure(new ChannelClosedException)) )
            w.markUsed()
          case None =>
            progress = true
            progressWaitWriter(w)
    }
    progress

  protected def processDoneClose(): Boolean  = {
    var progress = false
    while(!doneReaders.isEmpty) {
      val r = doneReaders.poll()
      if !(r eq null) && !r.isExpired then
        r.capture() match
          case Some(f) =>
            progress = true
            taskExecutor.execute(() => f(Success(())))
            r.markUsed()
          case None =>
            progressWaitDoneReader(r)
    }
    progress
  }
  

  protected def closeWriter(w: Writer[A]): Unit = {
    var done = false
    while (!done && !w.isExpired)
      w.capture() match
        case Some((a,f)) => 
          taskExecutor.execute(() => f(Failure(new ChannelClosedException)) )
          w.markUsed()
          done = true
        case None =>
          if (!w.isExpired) then
            Thread.onSpinWait()
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



