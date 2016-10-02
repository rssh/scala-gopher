package gopher.channels

import gopher._
import akka.actor._
import akka.pattern._
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util._
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentLinkedQueue


class Selector[A](api: GopherAPI) extends PromiseFlowTermination[A]
{

  thisSelector =>

  def addReader[E](ch:Input[E],f: ContRead[E,A] => Option[ContRead.In[E]=>Future[Continuated[A]]]): Unit =
  {
    waiters add makeLocked(ContRead(f, ch, this)) 
  }
 
  def addWriter[E](ch:Output[E], f: ContWrite[E,A] => Option[(E,Future[Continuated[A]])]): Unit =
  {
   waiters add makeLocked(ContWrite(f,ch,this))
  }

  def addTimeout(timeout:FiniteDuration, f: Skip[A] => Option[Future[Continuated[A]]]):Unit =
  {
   if (!timeoutRecord.isDefined) {
     timeoutRecord.lastNOperations = nOperations.get
     timeoutRecord.timeout = timeout
     timeoutRecord.waiter = makeLocked(Skip(f,this))
   } else {
     throw new IllegalStateException("select must have only one timeout entry")
   }
  }

  def run:Future[A] =
  {
    sendWaits()
    if (timeoutRecord.isDefined) {
       scheduleTimeout()
    }
    future
  }



  private[this] def makeLocked(block: Continuated[A]): Continuated[A] =
  {
      block match {
           case cr@ContRead(f,ch, ft) => 
               def f1(cont:ContRead[cr.El,cr.R]): Option[ContRead.In[cr.El]=>Future[Continuated[cr.R]]]  = { 
                     System.err.println(s"Selector.makeLocked.f1 - before tryLocked, fClass=${f.getClass} isLocked=${isLocked}")
                     tryLocked(f(ContRead(f,ch,ft)),cont,"read") map { q =>
                                   (in => unlockAfter(
                                                 try { 
                                                   q(in) 
                                                 }catch{
                                                   case e: Throwable => ft.doThrow(e)
                                                                        Future successful Never
                                                 },
                                                 ft,"read"))
                     }
               }
               ContRead(f1,ch,ft)
           case cw@ContWrite(f,ch, ft) => 
               val f2: ContWrite[cw.El,cw.R] => Option[(cw.El,Future[Continuated[cw.R]])] = 
                               { (cont) =>
                                 tryLocked(f(ContWrite(f,ch,ft)), cont, "write") map {
                                       case (a,future) =>
                                            (a,unlockAfter(future, ft ,"write"))
                                 }
                               }
                               ContWrite(f2,ch,ft)
           case sk@Skip(f,ft) => 
                                def f3(cont: Skip[sk.R]):Option[Future[Continuated[sk.R]]] = 
                                  tryLocked(f(Skip(f,ft)), cont , "skip") map {
                                      unlockAfter(_,ft,"skip")
                                  }
                                Skip(f3,ft)
           case dn@Done(f,ft) => dn
           case Never => Never 
      }
  }

  
  @inline
  private[this] def tryLocked[X](body: => Option[X], cont: FlowContinuated[A], dstr: String):Option[X] =
       if (tryLock()) {
           System.err.println(s"lock set for selector ${this}")
           try {
             body match {
               case None => mustUnlock(dstr,cont.flowTermination)
                            waiters add cont
                            None
               case sx@Some(x) =>
                            nOperations.incrementAndGet()
                            sx
             }
           }catch{
             case ex: Throwable => 
                     unlock(dstr)
                     cont.flowTermination.doThrow(ex)
                     None
           }
        } else {
           System.err.println(s"TryLockedFailed, send $cont to waiters")
           toWaiters(cont)
           None
        }
            

  @inline
  private[this] def unlockAfter(f:Future[Continuated[A]], flowTermination: FlowTermination[A], dstr: String): Future[Continuated[A]] =
    f.transform(
         next => { 
                   if (mustUnlock(dstr,flowTermination)) {
                         if (timeoutRecord.isDefined)
                             scheduleTimeout()
                         makeLocked(next)
                   } else Never 
                 },
         ex =>   { mustUnlock( dstr, flowTermination); ex }
    )

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
     System.err.println(s"unlock selector ${this}")
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

  private[this] val processor = api.continuatedProcessorRef

  private[this] implicit val executionContext: ExecutionContext = api.executionContext
  

}



