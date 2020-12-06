package gopher.impl

import cps._
import gopher._

import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicReferenceArray
import java.util.concurrent.atomic.AtomicInteger
import scala.util.Try
import scala.util.Success
import scala.util.Failure

import GuardedSPSCBaseChannel._


class GuardedSPSCUnbufferedChannel[F[_]:CpsAsyncMonad,A](
    gopherApi: JVMGopher[F],
    controlExecutor: ExecutorService, 
    taskExecutor: ExecutorService) extends GuardedSPSCBaseChannel[F,A](gopherApi,controlExecutor, taskExecutor):

    protected override def step(): Unit = {
      var progress = true 
      while (progress) {
        var readerLoopDone = false
        progress = false
        while(!readerLoopDone && !readers.isEmpty && !writers.isEmpty) {
              val reader = readers.poll()
              if (!(reader eq null) && !reader.isExpired) then
                var writersLoopDone = false
                while(! writersLoopDone && !readerLoopDone && !writers.isEmpty) {
                   var writer = writers.poll()
                   if (!(writer eq null) && !writer.isExpired) then
                     // now we have reader and writer
                     reader.capture() match
                        case Some(readFun) =>
                          writer.capture() match
                            case Some((a,writeFun)) =>
                              // great, now we have all 
                              taskExecutor.execute(()=>readFun(Success(a)))
                              taskExecutor.execute(()=>writeFun(Success(())))
                              progress = true
                              reader.markUsed()
                              writer.markUsed()
                              writersLoopDone = true
                            case None =>
                              // reader same, other writer
                              reader.markFree()
                              progress = true // return when
                              if (!writer.isExpired)
                                writers.addLast(writer)
                                Thread.onSpinWait() 
                        case None =>
                          writers.addFirst(writer)
                          writersLoopDone = true
                          progress = true
                          if (!reader.isExpired)
                            readers.addLast(reader)
                            Thread.onSpinWait()
                }  
        }
        if (!progress) then
          if !checkLeaveStep() then
            progress = true
      }
    }


