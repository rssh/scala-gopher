package gopher.channels

import gopher._
import akka.actor._
import akka.pattern._

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util._
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}
import java.util.concurrent.ConcurrentLinkedQueue


class Selector[A](api: GopherAPI) extends PromiseFlowTermination[A] {

  thisSelector =>

  def addReader[E](ch: Input[E], f: ContRead[E, A] => Option[ContRead.In[E] => Future[Continuated[A]]]): Unit = {
    waiters add makeLocked(ContRead(f, ch, this))
  }

  def addWriter[E](ch: Output[E], f: ContWrite[E, A] => Option[(E, Future[Continuated[A]])]): Unit = {
    waiters add makeLocked(ContWrite(f, ch, this))
  }

  def addTimeout(timeout: FiniteDuration, f: Skip[A] => Option[Future[Continuated[A]]]): Unit = {
    if (!timeoutRecord.isDefined) {
      timeoutRecord.lastNOperations = nOperations.get
      timeoutRecord.timeout = timeout
      timeoutRecord.waiter = makeLocked(Skip(f, this))
    } else {
      throw new IllegalStateException("select must have only one timeout entry")
    }
  }

  def addErrorHandler(f: (ExecutionContext, FlowTermination[A], Continuated[A], Throwable) => Future[Continuated[A]]): Unit = {
    errorHandler.lazySet(f)
  }

  def run: Future[A] = {
    sendWaits()
    if (timeoutRecord.isDefined) {
      scheduleTimeout()
    }
    future
  }


  private[channels] def lockedRead[E](f: ContRead.AuxF[E, A], ch: Input[E], ft: FlowTermination[A]): ContRead.AuxF[E, A] = {
    val cont0 = ContRead(f,ch,ft)
    def f1(cont: ContRead[E, A]): Option[ContRead.In[E] => Future[Continuated[A]]] = {
      tryLocked(f(cont0), cont, "read") map { q =>
        in =>
          unlockAfter(
            try {
              errorHandled(q(in), cont0)
            } catch {
              case e: Throwable => ft.doThrow(e)
                Future successful Never
            },
            ft, "read")
      }
    }

    f1
  }



  private[channels] def lockedWrite[E](f: ContWrite.AuxF[E, A], ch: Output[E], ft: FlowTermination[A]): ContWrite.AuxF[E, A] = { (cont) =>
    val cont0 = ContWrite(f,ch,ft)
    tryLocked(f(cont0), cont, "write") map {
      case (a, future) =>
        (a, unlockAfter(errorHandled(future,cont0), ft, "write"))
    }
  }

  private[channels] def lockedSkip(f: Skip.AuxF[A], ft: FlowTermination[A]): Skip.AuxF[A] = { cont =>
    // TODO: check, maybe pass cont in this situation is enough ?
    val cont0 = Skip(f,ft)
    tryLocked(f(cont0), cont, "skip") map { f =>
      unlockAfter( errorHandled(f, cont0), ft, "skip")
    }
  }

  private[channels] def makeLocked(block: Continuated[A]): Continuated[A] = {
    block match {
      case cr@ContRead(f, ch, ft) => ContRead(lockedRead(f, ch, ft), ch, ft)
      case cw@ContWrite(f, ch, ft) => ContWrite(lockedWrite(f, ch, ft), ch, ft)
      case sk@Skip(f, ft) => Skip(lockedSkip(f, ft), ft)
      case dn@Done(f, ft) => dn
      case Never => Never
    }
  }


  @inline
  private[this] def tryLocked[X](body: => Option[X], cont: FlowContinuated[A], dstr: String): Option[X] =
    if (tryLock()) {
      try {
        body match {
          case None => mustUnlock(dstr, cont.flowTermination)
            waiters add cont
            None
          case sx@Some(x) =>
            nOperations.incrementAndGet()
            sx
        }
      } catch {
        case ex: Throwable =>
          if (!(errorHandler.get() eq null)) {
            errorHandler.get()
          }
          unlock(dstr)
          cont.flowTermination.doThrow(ex)
          None
      }
    } else {
      toWaiters(cont)
      None
    }

  @inline
  private[channels] def errorHandled(f: Future[Continuated[A]], cont: Continuated[A]): Future[Continuated[A]] =
  {
    f.recoverWith{
      case ex =>
        val h = errorHandler.get()
        if (h eq null) Future failed ex
        else h(executionContext, this, cont,ex)
    }
  }

  @inline
  private[channels] def unlockAfter(f: Future[Continuated[A]], flowTermination: FlowTermination[A], dstr: String): Future[Continuated[A]] = {
    f.transform(
      next => {
        if (mustUnlock(dstr, flowTermination)) {
          if (timeoutRecord.isDefined)
            scheduleTimeout()
          makeLocked(next)
        } else Never
      },
      ex => {
        mustUnlock(dstr, flowTermination);
        ex
      }
    )
  }

  private[this] def toWaiters(cont:Continuated[A]):Unit=
  {
   waiters.add(cont) 
   if (!lockFlag.get()) {
      // possible, when we call waiters.put locked, but then in other thread it was 
      // unlocked and queue cleaned before waiters modify one.
      sendWaits()
   }
  }


  private[this] def scheduleTimeout():Unit =
  {

    def tickOperation():Unit =
    {
     if (!isCompleted && timeoutRecord.isDefined) {
        val currentNOperations = nOperations.get()
        if (currentNOperations == timeoutRecord.lastNOperations) {
           // fire
           timeoutRecord.waiter match {
              // TODO: add timeout field to skip
             case sk@Skip(f,ft) => f(sk) foreach { futureNext =>
              futureNext.onComplete {
                case Success(next) => if (!isCompleted) {
                                       next match {
                                        case sk@Skip(f,ft) if (ft eq this) => timeoutRecord.waiter = sk
                                        case other => 
                                                   timeoutRecord.waiter = Never
                                                   api.continuatedProcessorRef ! other
                                       }
                                      }
                case Failure(ex) => if (!isCompleted) ft.doThrow(ex)
              }
             }
             case other => api.continuatedProcessorRef ! other
           }
        }
      }
    }

    if (timeoutRecord.isDefined) {
      // TODO: make CAS 
      timeoutRecord.lastNOperations = nOperations.get()
      val scheduler = api.actorSystem.scheduler
      scheduler.scheduleOnce(timeoutRecord.timeout)(tickOperation)
    }

  }

  def isLocked: Boolean = lockFlag.get()

  private[this] def tryLock(): Boolean = lockFlag.compareAndSet(false,true)

  private[this] def unlock(debugFrom: String): Boolean =
  {
     val retval = lockFlag.compareAndSet(true,false)
     //if (retval) {
        sendWaits()
     //}
     retval
  }

  private[this] def mustUnlock(debugFrom: String, ft: FlowTermination[_]): Boolean =
  {
    if (!unlock(debugFrom)) {
     try {
       throw new IllegalStateException("other fiber occypied select 'lock'")
     }catch{
       //!!!
       case ex: Exception => ft.doThrow(ex)
                ex.printStackTrace()
     }
     false
    } else true
  }

  

  private[this] def sendWaits(waiters: ConcurrentLinkedQueue[Continuated[A]] = waiters): Unit =
  {
   // concurrent structure fpr priority queue
   var skips = List[Continuated[A]]()
   var nSend = 0
   while(!waiters.isEmpty && !lockFlag.get()) {
      val c = waiters.poll
      if (!(c eq null)) {
          nSend = nSend + 1
          c match {
             case sk@Skip(_,_) => 
             skips = c.asInstanceOf[Continuated[A]]::skips
          case _ =>
             processor ! c
          }
      }
   }
   if (!lockFlag.get) {
       //planIdle
       //TODO: plan instead direct send.
       for(c <- skips) {
             (processor.ask(c)(10 seconds)).foreach(x =>
                        waiters.add(x.asInstanceOf[Continuated[A]])
             )
       }
     }
   }


  private[this] val log = api.actorSystem.log

  // false when unlocked, true otherwise.
  private[this] val lockFlag: AtomicBoolean = new AtomicBoolean(false)
  
  // number of operations, increased during each lock/unlock.
  //  used for idle and timeout detection 
  private[channels] val nOperations = new AtomicLong();

  private[this] val waiters: ConcurrentLinkedQueue[Continuated[A]] = new ConcurrentLinkedQueue()

  private[this] class TimeoutRecord(
                        var lastNOperations: Long,
                        var timeout: FiniteDuration,
                        var waiter: Continuated[A]
                      )  {
     def isDefined:Boolean = (waiter != Never)
  }


  private[this] val timeoutRecord: TimeoutRecord = new TimeoutRecord(0L,0 milliseconds, Never)

  private[this] val errorHandler = new AtomicReference[(ExecutionContext, FlowTermination[A], Continuated[A], Throwable) => Future[Continuated[A]]]()
  private[this] val inError = new AtomicBoolean(false)

  private[this] val processor = api.continuatedProcessorRef

  private[this] implicit val executionContext: ExecutionContext = api.executionContext
  

}



