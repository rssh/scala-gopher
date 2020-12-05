package gopher.impl

import cps._
import gopher._
import scala.collection.mutable.Queue
import scala.scalajs.concurrent.JSExecutionContext
import scala.util._
import scala.util.control.NonFatal


class UnbufferedChannel[F[_]:CpsAsyncMonad, A](gopherApi: JSGopher[F]) extends Channel[F,A,A]:

  private val readers: Queue[Reader[A]] = Queue.empty
  private val writers: Queue[Writer[A]] = Queue.empty
  
  private var value: Option[A] = None
  private var closed: Boolean = false

  protected override def asyncMonad: cps.CpsAsyncMonad[F] = summon[CpsAsyncMonad[F]]

  def addReader(reader: Reader[A]): Unit = 
      readers.enqueue(reader)
      process()

  def addWriter(writer: Writer[A]): Unit =
      writers.enqueue(writer)
      process()

  private def process(): Unit = 
    var progress = true
    while(progress)
      value match
        case Some(a) => progress = processReaders(a)
        case None => progress = processWriters()

  private def processReaders(a:A): Boolean =
    // precondition: value == Some()
    var progress = false
    if (!readers.isEmpty) then
      val reader = readers.dequeue()
      progress = true
      reader.capture() match
        case Some(f) =>
          value = None
          JSExecutionContext.queue.execute{()=>
            try 
              f(Success(a))
            catch
              case ex: Throwable =>
                // TODO: set execution handler.
                ex.printStackTrace()
                if (false) {
                  JSExecutionContext.queue.execute{ ()=>throw ex }
                }
          }
        case None =>
    progress  

  private def processWriters(): Boolean =
    var progress = false
    if (!writers.isEmpty) then
      val writer = writers.dequeue()
      writer.capture() match
        case Some((a,f)) => 
          value = Some(a)
          JSExecutionContext.queue.execute{ () =>
            try
              f(Success(())) 
            catch 
              case NonFatal(ex) =>
                ex.printStackTrace()
                if (false) {
                  JSExecutionContext.queue.execute( ()=> throw ex )
                }
          }
        case None =>
          // skip
    progress