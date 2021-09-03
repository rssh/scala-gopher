package gopher.impl

import cps._
import gopher._
import scala.collection.mutable.Queue
import scala.scalajs.concurrent.JSExecutionContext
import scala.util._
import scala.util.control.NonFatal
import java.util.logging.Level


abstract class BaseChannel[F[_],A](override val gopherApi: JSGopher[F]) extends Channel[F,A,A]:

  protected val readers: Queue[Reader[A]] = Queue.empty
  protected val writers: Queue[Writer[A]] = Queue.empty
  protected val doneReaders: Queue[Reader[Unit]] = Queue.empty 
  protected var closed: Boolean = false

  override def close(): Unit =
    closed = true
    processClose()

  override def isClosed: Boolean =
    closed

  protected def submitTask(f: ()=>Unit ): Unit =
    JSExecutionContext.queue.execute{ () =>
      try
        f()
      catch
        case NonFatal(ex) =>
          if (true) then
            gopherApi.log(Level.WARNING, "impossible: exception in channel callback", ex)
            ex.printStackTrace()
          if (false) then
            JSExecutionContext.queue.execute(  ()=> throw ex )  
    }

  def addReader(reader: Reader[A]): Unit = 
    readers.enqueue(reader)
    process()
    
  def addWriter(writer: Writer[A]): Unit =
    if (closed) {
        writer.capture() match
          case Expirable.Capture.Ready((a,f)) =>
            writer.markUsed()
            submitTask( () =>
              f(Failure(new ChannelClosedException()))
            )
          case _ =>  
    } else {
        writers.enqueue(writer)
        process()
    }

  def addDoneReader(reader: Reader[Unit]): Unit =
    if (closed && isEmpty) {
        reader.capture() match
          case Expirable.Capture.Ready(f) =>
            reader.markUsed()
            submitTask( () => f(Success(())))
          case Expirable.Capture.WaitChangeComplete =>
            // mb is blocked and will be evaluated in 
            doneReaders.enqueue(reader)
            process()
          case Expirable.Capture.Expired  =>
    } else {
        doneReaders.enqueue(reader)
        process()
    }    

  protected def processClose(): Unit =
    if (isEmpty) then
      processCloseDone()
      submitTask(processCloseReaders)
    submitTask(processCloseWriters)
         
  protected def exhauseQueue[T <: Expirable[A],A](queue: Queue[T], action: A => Unit): Unit =
    while(!queue.isEmpty) {
      val v = queue.dequeue()
      if (!v.isExpired) then
        v.capture() match
          case Expirable.Capture.Ready(a) =>
            v.markUsed()
            action(a)
          case _ =>
            // do nothing.
            //   exists case, when this is possible: wheb we close channel from
            // select-group callback, which is evaluated now.
            //  in this case we will see one as evaluating.       
    }

  protected def processCloseDone(): Unit =
    val success = Success(())
    exhauseQueue(doneReaders, f => f(success))

    
  protected def processCloseReaders(): Unit =
    val channelClosed = Failure(ChannelClosedException())
    exhauseQueue(readers, f => f(channelClosed))    

  protected def processCloseWriters(): Unit =
    val channelClosed = Failure(ChannelClosedException())
    exhauseQueue(writers, { case (a,f) => f(channelClosed) })
 
  protected def isEmpty: Boolean
  
  protected def process(): Unit

    

