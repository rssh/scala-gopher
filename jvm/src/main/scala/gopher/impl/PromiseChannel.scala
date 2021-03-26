package gopher.impl

import cps._
import gopher._
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import scala.util.Try
import scala.util.Success
import scala.util.Failure


/**
 * Channel is closed immediatly after successfull write.
 **/
 class PromiseChannel[F[_],A](override val gopherApi: JVMGopher[F], taskExecutor: ExecutorService) extends Channel[F,A,A]:

    protected val readers = new ConcurrentLinkedDeque[Reader[A]]()
    protected val doneReaders = new ConcurrentLinkedDeque[Reader[Unit]]()
    protected val ref: AtomicReference[AnyRef | Null] = new AtomicReference(null)
    protected val closed: AtomicBoolean = new AtomicBoolean(false)
    protected val readed: AtomicBoolean = new AtomicBoolean(false)

    def addReader(reader: Reader[A]): Unit =
        readers.add(reader)
        step()
          
    def addWriter(writer: Writer[A]): Unit =
        var done = false
        while(!done && !writer.isExpired)
          writer.capture() match
            case Some((a,f)) =>
              val ar: AnyRef = a.asInstanceOf[AnyRef] //
              if (ref.compareAndSet(null,ar) && !closed.get() ) then
                closed.lazySet(true)
                taskExecutor.execute(()=> f(Success(())))
                writer.markUsed()
                step()
              else 
                taskExecutor.execute(() => f(Failure(new ChannelClosedException())))
                writer.markUsed() 
              done = true
            case None =>   
              if (!writer.isExpired) then
                Thread.onSpinWait()
              

    def addDoneReader(reader: Reader[Unit]): Unit =
      if (!closed.get()) then
        doneReaders.add(reader)
      else 
        var done = false
        while(!done & !reader.isExpired) {
          reader.capture() match
            case Some(f) => 
              reader.markUsed()
              taskExecutor.execute(()=>f(Success(())))
              done = true
            case None =>
              if (!reader.isExpired)
                Thread.onSpinWait()
        }



    def close(): Unit =
      closed.set(true)
      if (ref.get() eq null) 
        closeAll() 
    
    def isClosed: Boolean =
      closed.get()    
    
    def step(): Unit =
      val ar = ref.get()
      if !(ar eq null) then
        var done = false
        while(!done && !readers.isEmpty) {
          val r = readers.poll()
          if ! (r eq null) then
              while (!done && !r.isExpired) {
                 r.capture() match
                  case Some(f) =>
                    done = true
                    r.markUsed()
                    if (readed.compareAndSet(false,true)) then
                      val a = ar.nn.asInstanceOf[A]
                      taskExecutor.execute(() => f(Success(a)))
                    else
                      taskExecutor.execute(() => f(Failure(new ChannelClosedException())))
                  case None =>
                    if (!r.isExpired) {
                      if (readers.isEmpty)
                        Thread.onSpinWait()
                      readers.addLast(r)
                    }
              }
        }
      else if (closed.get()) then
        closeAll()

    def closeAll(): Unit =
      while(!doneReaders.isEmpty) {
        val r = doneReaders.poll()
        if !((r eq null) || r.isExpired) then
          r.capture() match
            case Some(f) => 
              r.markUsed()
              taskExecutor.execute(()=>f(Success(())))
            case None =>
              if (!r.isExpired) then
                if (doneReaders.isEmpty) then
                  Thread.onSpinWait()
                doneReaders.addLast(r)
      }
      while(!readers.isEmpty) {
        val r = readers.poll()
        if (!(r eq null) && !r.isExpired) then
          r.capture() match
            case Some(f) =>
              r.markUsed()
              taskExecutor.execute(() => f(Failure(new ChannelClosedException)))
            case None =>
              if (!r.isExpired) then
                if (readers.isEmpty) then
                  Thread.onSpinWait()
                readers.addLast(r)
      }



      
