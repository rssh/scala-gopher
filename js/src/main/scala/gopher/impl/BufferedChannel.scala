package gopher.impl

import cps._
import gopher._
import scala.collection.mutable.Queue
import scalajs.js
import scalajs.concurrent.JSExecutionContext
import scala.util._
import scala.util.control.NonFatal

class BufferedChannel[F[_]:CpsAsyncMonad, A](gopherApi: JSGopher[F], bufSize: Int) extends BaseChannel[F,A](gopherApi):

  val  ringBuffer: js.Array[A] = new js.Array[A](bufSize)
  var  start: Int = 0
  var  size: Int = 0

  //  [1] [2] [3]
  //  ˆ        ˆ

  def  isEmpty = (size == 0)

  def  nElements = size
  
  def isFull = (size == bufSize)

  protected def internalDequeuePeek(): Option[A] = 
    if isEmpty then None else Some(ringBuffer(start))
  
  protected def internalDequeueFinish(): Unit = 
    require(size > 0)
    start = (start + 1) % bufSize
    size = size - 1

  protected def internalEnqueue(a:A): Boolean =
    if size < bufSize then
      val end = (start + size) % bufSize
      ringBuffer(end) = a
      size = size + 1
      true
    else
      false
    

  protected def process(): Unit = 
    var progress = true
    while(progress)
      progress = false
      internalDequeuePeek() match 
        case Some(a) => 
           progress |= processReaders(a)
        case None =>
           // nothing.
      progress |= processWriters()
    if (closed) then
      processClose()
                
  protected def processReaders(a:A): Boolean =
    var progress = false
    if (!readers.isEmpty && !isEmpty) then
      val reader = readers.dequeue()
      progress = true
      reader.capture().foreach{ f => 
        internalDequeueFinish()
        reader.markUsed()
        submitTask( () => f(Success(a)) )
      }
    progress  
          
  protected def processWriters(): Boolean =
    var progress = false
    if (!writers.isEmpty && !isFull) then
      val writer = writers.dequeue()
      writer.capture() match
        case Some((a,f)) =>
          internalEnqueue(a)
          writer.markUsed()
          submitTask(  () => f(Success(())) )
          progress = true
        case None =>
          if (!writer.isExpired) then
            // impossible, we have no parallel execution
            println(s"Deadlock detected, this=${this}, writer=${writer}, writer.isExpired=${writer.isExpired}, writer.capture=${writer.capture()}")
            throw DeadlockDetected()
    progress

  


      


  
  

