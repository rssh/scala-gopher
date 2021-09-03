package gopher.impl

import cps._
import gopher._
import scala.collection.mutable.Queue
import scala.scalajs.concurrent.JSExecutionContext
import scala.util._
import scala.util.control.NonFatal

class PromiseChannel[F[_]:CpsAsyncMonad, A](gopherApi: JSGopher[F]) extends BaseChannel[F,A](gopherApi):

  private var value: Option[A] = None
  private var readed = false
  
  protected def isEmpty: Boolean = value.isEmpty || readed

  //override def addDoneReader(reader: Reader[Unit]): Unit =

  protected def process(): Unit =
    var done = false
    // we have only one writer.
    while (!writers.isEmpty && value.isEmpty) {
      val w = writers.dequeue()
      w.capture() match
          case Expirable.Capture.Ready((a,f)) =>
            w.markUsed()
            submitTask(()=>f(Success(())))
            value = Some(a)
            closed = true
            // we can't havw more than one unexpired 
          case Expirable.Capture.WaitChangeComplete =>
              // impossible in js, 
              //  (mb processNextTick()?)
              throw new DeadlockDetected()
          case Expirable.Capture.Expired =>
    }
    if (!readers.isEmpty && value.isDefined) {
      while(!readers.isEmpty && !readed) {
        val r = readers.dequeue()
        r.capture() match
            case Expirable.Capture.Ready(f) =>
                r.markUsed()
                submitTask(()=>f(Success(value.get)))
                readed = true
            case Expirable.Capture.WaitChangeComplete =>
                throw new DeadlockDetected()  
            case Expirable.Capture.Expired =>
      }
      //if (readed) {
       // processCloseDone()
      //}
    }
    if (closed) then
      processClose()
    

  
  
    