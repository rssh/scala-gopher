package gopher.impl

import cps._
import gopher._
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicReferenceArray
import java.util.concurrent.atomic.AtomicInteger
import scala.util.Try
import scala.util.Success
import scala.util.Failure

import java.util.logging.{Level => LogLevel}

class GuardedSPSCBufferedChannel[F[_]:CpsAsyncMonad,A](gopherApi: JVMGopher[F], bufSize: Int,
controlExecutor: ExecutorService, 
taskExecutor: ExecutorService) extends GuardedSPSCBaseChannel[F,A](gopherApi,controlExecutor, taskExecutor):

    import GuardedSPSCBaseChannel._
  
    class RingBuffer extends SPSCBuffer[A] {
         
        val refs: AtomicReferenceArray[AnyRef | Null] = new AtomicReferenceArray(bufSize);
        val publishedStart: AtomicInteger = new AtomicInteger(0)
        val publishedSize: AtomicInteger = new AtomicInteger(0)

        var start: Int = 0
        var size: Int = 0

        override def local(): Unit = {
          start = publishedStart.get()
          size = publishedSize.get()
        }

        override def publish(): Unit = {
          publishedStart.set(start)
          publishedSize.set(size)
        }

        override def isEmpty(): Boolean = (size == 0)
        override def isFull(): Boolean = (size == bufSize)

        override def startRead(): A = {
          val aRef = refs.get(start)
          //TODO: enable debug mode
          //if (aRef eq null) {   
          //   throw new IllegalStateException("read null item")
          //}
          aRef.nn.asInstanceOf[A]
        }

        override def finishRead(): Boolean = {
          if (size > 0) then
            start = (start + 1) % bufSize 
            size = size - 1
            true
          else
            false  
        }

        override def write(a:A): Boolean = {
          if (size < bufSize) then
            val end = (start + size) % bufSize
            val aRef: AnyRef | Null = a.asInstanceOf[AnyRef] // boxing
            refs.lazySet(end,aRef)
            size += 1
            true
          else
            false
        }        

    }

      //Mutable buffer state
    protected val state: SPSCBuffer[A] = new RingBuffer()


    protected def step(): Unit = 
      state.local()
      var isClosed = publishedClosed.get()
      var progress = true
      while(progress) {
        progress = false
        if !state.isEmpty() then 
          progress |= processReadsStep()
        else
          if isClosed then 
            progress |= processDoneClose()
            progress |= processReadClose()
        if (!state.isFull() && !isClosed) then
          progress |= processWriteStep()
        if (isClosed)
           progress |= processWriteClose()
        if (!progress) {
            state.publish()
            if (! checkLeaveStep()) {
              progress = true
              isClosed = publishedClosed.get()
            } 
        }
      }

  
      
    private def processReadsStep(): Boolean =
        // precondition: !isEmpty
        val a = state.startRead()
        var done = false
        var progress = false
        var nonExpiredBusyReads = scala.collection.immutable.Queue.empty[Reader[A]]
        while(!done && !readers.isEmpty) {
          val reader = readers.poll()
          if !(reader eq null) && !reader.isExpired then
            reader.capture() match
              case Some(f) =>
                // try/cath arround f is a reader reponsability
                taskExecutor.execute(() => f(Success(a)))  
                reader.markUsed()
                state.finishRead()
                progress = true
                done = true
              case None =>
                if !reader.isExpired then
                  nonExpiredBusyReads = nonExpiredBusyReads.enqueue(reader) 
        }
        while(nonExpiredBusyReads.nonEmpty) {
          // not in this thread, but progress.
          progress = true
          val (r, c) = nonExpiredBusyReads.dequeue
          progressWaitReader(r)
          nonExpiredBusyReads = c
        }
        progress
  
    // precondition: ! isFUll    
    private def processWriteStep(): Boolean  = 
      var progress = false
      var done = false
      var nonExpiredBusyWriters = scala.collection.immutable.Queue.empty[Writer[A]]
      while(!done && !writers.isEmpty) {
        val writer = writers.poll()
        if !(writer eq null ) && ! writer.isExpired then
          writer.capture() match
            case Some((a,f)) => 
              done = true
              if (state.write(a)) then 
                 taskExecutor.execute(() => f(Success(())))
                 progress = true
                 writer.markUsed()
              else
                 // impossible, because state
                 //TODO: log
                 //log("impossibe,unsuccesfull write after !isFull")
                 writer.markFree()
                 writers.addFirst(writer)
            case None =>
              if (!writer.isExpired)
                nonExpiredBusyWriters = nonExpiredBusyWriters.enqueue(writer)
      }
      while(nonExpiredBusyWriters.nonEmpty) {
        progress = true
        val (w, c) = nonExpiredBusyWriters.dequeue
        nonExpiredBusyWriters = c
        progressWaitWriter(w)
      } 
      progress
  

end GuardedSPSCBufferedChannel
