package gopher.impl

import cps._
import gopher._
import scala.collection.mutable.Queue
import scala.scalajs.concurrent.JSExecutionContext
import scala.util._
import scala.util.control.NonFatal


class UnbufferedChannel[F[_]:CpsAsyncMonad, A](gopherApi: JSGopher[F]) extends BaseChannel[F,A](gopherApi):
  
  private var value: Option[A] = None

  protected override def internalDequeuePeek(): Option[A] = value
  
  protected override def internalDequeueFinish(): Unit = 
    value = None

  protected def internalEnqueue(a:A): Boolean =
    value match
      case None => value = Some(a)
                   true
      case Some(a1) => false


