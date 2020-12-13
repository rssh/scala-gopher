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
 class PromiseChannel[F[_]:CpsAsyncMonad,A](gopherApi: JVMGopher[F], taskExecutor: ExecutorService) extends Channel[F,A,A]:

    protected val readers = new ConcurrentLinkedDeque[Reader[A]]()
    protected val ref: AtomicReference[AnyRef | Null] = new AtomicReference(null)
    protected val closed: AtomicBoolean = new AtomicBoolean(false)
    protected val readed: AtomicBoolean = new AtomicBoolean(false)

    protected override def asyncMonad = summon[CpsAsyncMonad[F]]

    def addReader(reader: Reader[A]): Unit =
        if (ref.get() eq null) then
          readers.add(reader)
          step()
        else
          var done = false
          while(!done && !reader.isExpired) {
            reader.capture() match
              case Some(f) => 
                f(Failure(new ChannelClosedException()))
                done = true
              case None => 
                if (!reader.isExpired) then
                  reader.markFree()
                  Thread.onSpinWait()
          }
          
    def addWriter(writer: Writer[A]): Unit =
        var done = false
        while(!done && !writer.isExpired)
          writer.capture() match
            case Some((a,f)) =>
              val ar: AnyRef = a.asInstanceOf[AnyRef] //
              if (ref.compareAndSet(null,ar) && !closed.get() ) then
                closed.lazySet(true)
                step()
                done = true
              else 
                f(Failure(new ChannelClosedException()))
                done = true
            case None =>   
              if (!writer.isExpired) {
                writer.markFree()
                Thread.onSpinWait()
              } 

    def close(): Unit =
      closed.set(true)
      if (ref.get() eq null) 
        closeAll() 
      
    
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
                    if (readed.compareAndSet(false,true)) then
                      val a = ar.nn.asInstanceOf[A]
                      f(Success(a))
                    else
                      f(Failure(new ChannelClosedException()))
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
      while(!readers.isEmpty) 
        val r = readers.poll()
        if (!(r eq null) && !r.isExpired) then
          r.capture() match
            case Some(f) =>
              f(Failure(new ChannelClosedException))
            case None =>
              if (!r.isExpired) then
                if (readers.isEmpty) then
                  Thread.onSpinWait()
                readers.addLast(r)
