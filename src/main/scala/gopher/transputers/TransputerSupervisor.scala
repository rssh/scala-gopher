package gopher.transputers

import gopher._
import akka.actor._
import scala.util._


/**
 * one actor, which perform operations for starting/stopping
 **/
class TransputerSupervisor(api: GopherAPI) extends Actor with ActorLogging 
{
  import TransputerSupervisor._
  
  implicit def ec =  api.executionContext

  def receive = {
     case Start(t) => log.debug(s"starting ${t}")
                        t.goOnce onComplete {
                             case scala.util.Success(x) => 
                                    api.transputerSupervisorRef ! Stop(t)
                             case scala.util.Failure(ex) => 
                                    api.transputerSupervisorRef ! Failure(t,ex)
                        }
     case Failure(t,ex) => 
                        handleFailure(t,ex)
     case Stop(t) => log.debug(s"${t} stopped")
                     if (!t.flowTermination.isCompleted) {
                         t.flowTermination.doExit(())
                     }
     case Escalate(t,ex) =>
                     t.flowTermination.doThrow(ex)
  }


  def handleFailure(t: Transputer, ex: Throwable)  =
  {
    import SupervisorStrategy.{Resume,Restart,Stop,Escalate}
    if (t.recoveryStatistics.failure(ex,t.recoveryPolicy,System.nanoTime)) {
        escalate(t, new Transputer.TooManyFailures(t))
    } else if (t.recoveryFunction.isDefinedAt(ex)) {
        t.recoveryFunction(ex) match {
           case Resume =>  log.debug(s"${t} failed with ${ex.getMessage()}, resume execution")
                           log.debug("caused by {}",ex)
                           t.beforeResume()
                           self ! Start(t)
           case Restart => log.debug(s"${t} failed with ${ex.getMessage()}, restart")
                           log.debug("caused by {}",ex)
                           val nt = t.recoverFactory()
                           nt.copyPorts(t)
                           nt.copyState(t)
                           nt.beforeRestart(t)
                           self ! Start(nt)
           case Stop =>    self ! TransputerSupervisor.Stop(t)
           case Escalate => log.debug(s"escalate from ${t} : ${ex}")
                            escalate(t,ex)
        }
    } else {
        escalate(t,ex)
    }
  }

  def escalate(t: Transputer, ex: Throwable): Unit =
  {
     self ! Escalate(t, ex)
     t.parent match {
        case Some(p) => self ! Failure(p,ex)
        case None => // root escalate, acccordint to akka rules: throw to supervisor of all system.
                     log.error(s"transputer exception escalated to root: ${ex.getMessage}")
                     throw ex;
     }
  }

}


object TransputerSupervisor
{
  sealed trait Message
  case class Start(t: Transputer) extends Message
  case class Failure(t: Transputer,ex: Throwable) extends Message
  case class Stop(t: Transputer) extends Message
  case class Escalate(t: Transputer, ex: Throwable) extends Message
}


