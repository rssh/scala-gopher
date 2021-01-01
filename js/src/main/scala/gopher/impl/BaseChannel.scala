package gopher.impl

import cps._
import gopher._
import scala.collection.mutable.Queue
import scala.scalajs.concurrent.JSExecutionContext
import scala.util._
import scala.util.control.NonFatal


abstract class BaseChannel[F[_],A](override val gopherApi: JSGopher[F]) extends Channel[F,A,A]:

  protected val readers: Queue[Reader[A]] = Queue.empty
  protected val writers: Queue[Writer[A]] = Queue.empty
  protected val doneReaders: Queue[Reader[Unit]] = Queue.empty 
  protected var closed: Boolean = false

  override def close(): Unit =
    closed = true
    processClose()

  protected def submitTask(f: ()=>Unit ): Unit =
    JSExecutionContext.queue.execute{ () =>
      try
        f()
      catch
        case NonFatal(ex) =>
          if (true) then
            ex.printStackTrace()
          if (false) then
            JSExecutionContext.queue.execute(  ()=> throw ex )  
    }

  def addReader(reader: Reader[A]): Unit = 
    readers.enqueue(reader)
    process()
    
  def addWriter(writer: Writer[A]): Unit =
    if (closed) {
        writer.capture().foreach{ (a,f) =>
          writer.markUsed()
          submitTask( () =>
            f(Failure(new ChannelClosedException()))
          )
        }
    } else {
        writers.enqueue(writer)
        process()
    }

  def addDoneReader(reader: Reader[Unit]): Unit =
    if (closed && isEmpty) {
        reader.capture() match
          case Some(f) =>
            reader.markUsed()
            submitTask( () => f(Success(())))
          case None =>
            // mb is blocked and will be evaluated in 
            doneReaders.enqueue(reader)
            process()
    } else {
        doneReaders.enqueue(reader)
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
          case Some(a) =>
            v.markUsed()
            action(a)
          case None =>
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

    

