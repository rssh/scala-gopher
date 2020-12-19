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
      if (closed && isEmpty ) {
        reader.capture().foreach{ f =>
            reader.markUsed()
            submitTask( () => 
              f(Failure(new ChannelClosedException()))
            )
        }
      } else {
        readers.enqueue(reader)
        process()
      }
    
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
          reader.capture().foreach{ f =>
            reader.markUsed()
            submitTask( () => f(Success(())))
          }
        } else {
          doneReaders.enqueue(reader)
        }    

  protected def processClose(): Unit =
    if (isEmpty) then
      processCloseDone()
      submitTask(processCloseWriters)
      submitTask(processCloseReaders)
          

  protected def processCloseDone(): Unit =
    val success = Success(())
    doneReaders.foreach( reader =>
      reader.capture().foreach{ f =>
         reader.markUsed()
         f(success)
      }
    )

  protected def processCloseReaders(): Unit =
    val channelClosed = Failure(ChannelClosedException())
    readers.foreach{ reader =>
      reader.capture().foreach{ f =>
         reader.markUsed()
         f(channelClosed)
      }
    }

  protected def processCloseWriters(): Unit =
    val channelClosed = Failure(ChannelClosedException())
    writers.foreach{ writer =>
      writer.capture().foreach{ (a,f) =>
         writer.markUsed()
         f(channelClosed)
      }
    }


  protected def isEmpty: Boolean
  
  protected def process(): Unit

    

