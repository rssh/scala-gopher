package gopher.impl

import cps._
import gopher._
import scala.collection.mutable.Queue
import scala.scalajs.concurrent.JSExecutionContext
import scala.util._
import scala.util.control.NonFatal


class UnbufferedChannel[F[_]:CpsAsyncMonad, A](gopherApi: JSGopher[F]) extends BaseChannel[F,A](gopherApi):
  

  protected def isEmpty: Boolean =
    writers.isEmpty

  protected def process(): Unit =
    var progress = true
    while(progress) {
      progress = false
      var done = false
      while(!done && !readers.isEmpty && !writers.isEmpty) {
         findReader() match
           case Some(reader) =>
             findWriter() match 
                case Some(writer) =>
                  reader.capture() match 
                    case Some(readFun) =>
                      writer.capture() match 
                        case Some((a,writeFun)) =>
                          submitTask( () => readFun(Success(a)))
                          submitTask( () => writeFun(Success(())) )
                          progress = true
                          done = true
                          writer.markUsed()
                          reader.markUsed()
                        case None =>
                          // impossible, because in js we have-no interleavinf, bug anyway
                          // let's fallback
                          reader.markFree()
                          readers.prepend(reader)
                    case None =>
                      // impossible, but let's fallback
                      writers.prepend(writer)
                case None =>
                  done = true
           case None => 
            done = true 
      }
    } 
    if (closed) {
       processClose()
    }


  private def findUnexpired[T <: Expirable[?]](q: Queue[T]): Option[T] =
    var retval: Option[T] = None
    while(retval.isEmpty && ! q.isEmpty) {
      val c = q.dequeue;
      if (!c.isExpired) {
        retval = Some(c)
      }
    }
    retval

  private def findReader(): Option[Reader[A]] =
    findUnexpired(readers)

  private def findWriter(): Option[Writer[A]] =
    findUnexpired(writers)
  
    