package gopher.impl

import cps._
import gopher._

import java.lang.Runnable
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import scala.util.Try
import scala.util.Success
import scala.util.Failure

class MTUnbufferedChannel[F[_]:CpsAsyncMonad,A <: AnyRef](controlExecutor: ExecutorService, taskExecutor: ExecutorService) extends Channel[F,A] with IOChannel[F,A,A] with OChannel[F,A] with IChannel[F,A]:

  private val readers = new ConcurrentLinkedDeque[Reader[A]]()
  private val writers = new ConcurrentLinkedDeque[Writer[A]]()
  private val ref = new AtomicReference[A|Null](null)
  private val isClosed = new AtomicBoolean(false)
  private val stepRunnable: Runnable = (()=>step())
  
  def addReader(reader: Reader[A]): Unit =
     if (reader.canExpire) then
        readers.removeIf( _.isExpired )        
     readers.add(reader)
     controlExecutor.submit(stepRunnable)

  def addWriter(writer: Writer[A]): Unit =
     if (writer.canExpire) then
        writers.removeIf( _.isExpired )        
     writers.add(writer)
     controlExecutor.submit(stepRunnable)



  // called only from control executor
  def step(): Unit = 
    var progress = true
    while(progress)
      progress = false
      val a: A|Null = ref.get()
      if !(a eq null) then
        val reader = readers.poll()
        if !(reader eq null) then
           progress = true
           reader.capture() match
             case Some(f) => 
                if (ref.compareAndSet(a,null))
                    taskExecutor.execute( ()=>f(Success(a.nn)) ) // TODO: what if f throws exception [?] 
                    reader.markUsed()
                else
                    // somebody from other thread stole our 'a', so need to try again
                    reader.markFree()
                    readers.addFirst(reader)
             case None => 
                    // Here should be next variant:
                    //  this select group is other thread in other channel 
                    //            if this call will successfull - reader become expired.
                    //            if not - we should not keep one here.  (but if it's bloked - we can put one at the end of the
                    //               tail to try next thing now)
                    //   (when this is one reader - this will create something like spin-lock, when we will have no real progres here,
                    //     but know, that some progress was in the other thread)
                    if (!reader.isExpired)
                        if (readers.isEmpty) Thread.`yield`()
                        readers.addLast(reader)
      else
        val writer = writers.poll()
        if !(writer eq null) then
           progress = true
           writer.capture() match
             case Some((a,f)) =>  
                 if (ref.compareAndSet(null,a)) 
                     taskExecutor.execute( () => f(Success(()))  )
                     writer.markUsed()
                 else
                     // somebody fill our references.
                     writer.markFree()
                     writers.addFirst(writer)
             case None =>
                     if (!writer.isExpired)
                        if (writers.isEmpty) Thread.`yield`()
                        writers.addLast(writer)


