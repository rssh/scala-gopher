package gopher.impl

import cps._
import gopher._
import scala.collection.mutable.Queue
import scala.scalajs.concurrent.JSExecutionContext
import scala.util._
import scala.util.control.NonFatal

class PromiseChannel[F[_]:CpsAsyncMonad, A](gopherApi: JSGopher[F]) extends BaseChannel[F,A](gopherApi):

  private var value: Option[A] = None

  protected def isEmpty: Boolean = value.isEmpty

  protected def process(): Unit =
    var done = false
    // we have only one writer.
    while (!writers.isEmpty && value.isEmpty) {
      val w = writers.dequeue()
      if (!w.isExpired) then
        w.capture() match
          case Some((a,f)) =>
            w.markUsed()
            submitTask(()=>f(Success(())))
            value = Some(a)
            closed = true
            // we can't havw more than one unexpired 
          case None =>
            if (!w.isExpired) then
              // impossible in js, 
              throw new DeadlockDetected()
    }
    if (!readers.isEmpty && value.isDefined) {
      var readed = false
      while(!readers.isEmpty) {
        val r = readers.dequeue()
        if (!r.isExpired) then
          r.capture() match
            case Some(f) =>
              if (!readed) then
                r.markUsed()
                submitTask(()=>f(Success(value.get)))
                readed = true
              else
                r.markFree()
                processCloseDone() // to have .done call befor channel-closed.
                if (!r.isExpired) then
                  r.capture().foreach{ f => 
                    r.markUsed()
                    submitTask(()=>f(Failure(ChannelClosedException())))
                  }
            case None =>
              if (!r.isExpired) 
                throw new DeadlockDetected()  
      }
    }
    if (closed) then
      processClose()
    


  
    