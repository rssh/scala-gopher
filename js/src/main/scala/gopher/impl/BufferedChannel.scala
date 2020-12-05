package gopher.impl

import cps._
import gopher._
import scala.collection.mutable.Queue
import scalajs.js
import scalajs.concurrent.JSExecutionContext
import scala.util._
import scala.util.control.NonFatal

class BufferedChannel[F[_]:CpsAsyncMonad, A](gopherApi: JSGopher[F], bufSize: Int) extends BaseChannel[F,A](gopherApi):

  val  ringBuffer: js.Array[A] = new js.Array[A](bufSize+1)
  var  start: Int = 0
  var  end: Int = 0

  //  [1] [2] [3]
  //  ˆ        ˆ

  def  isEmpty = (start == end)

  def  nElements =  if (end > start) then {
                       end - start 
                    } else if (start < end) then {
                       bufSize - start + end
                    } else 0
  
  def isFull = (nElements == bufSize)

  protected override def internalDequeuePeek(): Option[A] = 
    if isEmpty then None else Some(ringBuffer(start))
  
  protected override def internalDequeueFinish(): Unit = 
    start = (start + 1) % bufSize

  protected def internalEnqueue(a:A): Boolean =
    if (start < end) then
      if (end <= bufSize) then
          ringBuffer(end) = a
          end = end + 1
          true
      else if (start == 0) then
          false
      else
          ringBuffer(end) = a
          end = 0
          true
    else 
      if (end < start - 1) then
        ringBuffer(end) = a
        end = end + 1
        true
      else
        false

        



      


  
  

