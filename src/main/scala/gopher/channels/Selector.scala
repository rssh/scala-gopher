package gopher.channels

import gopher._
import akka.actor._
import akka.pattern._
import scala.concurrent._
import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentLinkedQueue


class Selector[A](api: GopherAPI) extends PromiseFlowTermination[A]
{

  thisSelector =>

  def addReader[E](ch:Input[E],f: (E, ContRead[E,A]) => Option[Future[Continuated[A]]]): Unit =
  {
    waiters add makeLocked(ContRead(f, ch, this)) 
  }
 
  def addWriter[E](ch:Output[E],f: ContWrite[E,A] => Option[(E,Future[Continuated[A]])]): Unit =
  {
   waiters add makeLocked(ContWrite(f,ch,this))
  }

  def addIdleSkip(f: Skip[A] => Option[Future[Continuated[A]]]): Unit =
  {
   idleWaiters add makeLocked(Skip(f,this))
  }

  def run:Future[A] =
  {
    sendWaits()
    api.idleDetector put this
    future
  }

  private[channels]  def startIdles: Unit =
  {
    if (idleWaiters.isEmpty) {
       api.idleDetector.remove(this)
    } else {
       sendWaits(idleWaiters) 
    }
  }

  private[this] def makeLocked(block: Continuated[A]): Continuated[A] =
  {
      block match {
           case cr@ContRead(f,ch, ft) => 
               val f1 : (cr.El, ContRead[cr.El,cr.R]) => Option[Future[Continuated[cr.R]]]  = { 
                             (a,cont) => 
                               tryLocked(f(a,ContRead(f,ch,ft)),cont,"read") map {
                                  unlockAfter(_,cont,"read")
                               }
               }
               ContRead(f1,ch, ft)
           case cw@ContWrite(f,ch, ft) => 
               val f2: ContWrite[cw.El,cw.R] => Option[(cw.El,Future[Continuated[cw.R]])] = 
                               { (cont) =>
                                 tryLocked(f(ContWrite(f,ch,ft)), cont, "write") map {
                                       case (a,future) =>
                                            (a,unlockAfter(future,cont,"write"))
                                 }
                               }
                               ContWrite(f2,ch,ft)
           case sk@Skip(f,ft) => val f3: Skip[sk.R] => Option[Future[Continuated[sk.R]]] = { 
                                cont =>
                                  tryLocked(f(Skip(f,ft)),cont, "skip") map {
                                      unlockAfter(_,cont,"skip")
                                  }
                                }
                                Skip(f3,ft)
           case dn@Done(f,ft) => dn
           case Never => Never 
      }
  }

  
  @inline
  private[this] def tryLocked[X](body: => Option[X], cont: FlowContinuated[A], dstr: String):Option[X] =
       if (tryLock()) {
           try {
             body match {
               case None => if (mustUnlock(dstr,cont.flowTermination)) {
                               waiters add cont
                            }
                            None
               case sx@Some(x) => {
                            nOperations.incrementAndGet()
                            sx
                            }
             }
           }catch{
             case ex: Throwable => cont.flowTermination.doThrow(ex)
             None
           }
        } else {
           toWaiters(cont)
           None
        }
            

  @inline
  private[this] def unlockAfter(f:Future[Continuated[A]], cont: FlowContinuated[A], dstr: String): Future[Continuated[A]] =
    f.transform(
         next => { if (mustUnlock(dstr,cont.flowTermination)) {
                         makeLocked(next)
                   } else Never 
                 },
         ex =>   { mustUnlock( dstr, cont.flowTermination); ex }
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


  def isLocked: Boolean = lockFlag.get();

  private[this] def tryLock(): Boolean = lockFlag.compareAndSet(false,true)

  private[this] def unlock(debugFrom: String): Boolean =
  {
     val retval = lockFlag.compareAndSet(true,false)
     if (retval) {
        sendWaits()
     }
     retval
  }

  private[this] def mustUnlock(debugFrom: String, ft: FlowTermination[_]): Boolean =
  {
    if (!unlock(debugFrom)) {
     try {
       throw new IllegalStateException("other fiber occypied select 'lock'");
     }catch{
       //!!!
       case ex: Exception => ft.doThrow(ex)
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

 

  // false when unlocked, true otherwise.
  private[this] val lockFlag: AtomicBoolean = new AtomicBoolean(false)

  // number of operations, increased during each lock/unlock.
  //  used for idle detection.
  private[channels] val nOperations = new AtomicLong();

  private[this] val waiters: ConcurrentLinkedQueue[Continuated[A]] = new ConcurrentLinkedQueue()
  private[this] val idleWaiters: ConcurrentLinkedQueue[Continuated[A]] = new ConcurrentLinkedQueue()

  private[this] val processor = api.continuatedProcessorRef

  private[this] implicit val executionContext: ExecutionContext = api.executionContext
  


}




