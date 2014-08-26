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

  def addReader[E](ch:Input[E],f: (E, ContRead[E,A]) => Option[Future[Continuated[A]]], priority:Int): Unit =
  {
    waiters.put(makeLocked(ContRead(f, ch, this), priority),priority)
  }
 
  def addWriter[E](ch:Output[E],f: ContWrite[E,A] => Option[(E,Future[Continuated[A]])], priority:Int ): Unit =
  {
   waiters.put(makeLocked(ContWrite(f,ch,this), priority), priority)
  }

  def addSkip(f: Skip[A] => Option[Future[Continuated[A]]], priority: Int): Unit =
  {
   waiters.put(makeLocked(Skip(f,this), priority), priority)
  }

  def run:Future[A] =
  {
    sendWaits
    future
  }

  private def makeLocked(block: Continuated[A], priority: Int): Continuated[A] =
  {
      block match {
           case cr@ContRead(f,ch, ft) => 
               val f1 : (cr.El, ContRead[cr.El,cr.R]) => Option[Future[Continuated[cr.R]]]  = { 
                             (a,cont) => 
                               tryLocked(f(a,ContRead(f,ch,ft)),cont,priority,"read") map {
                                  unlockAfter(_,cont,priority,"read")
                               }
               }
               ContRead(f1,ch, ft)
           case cw@ContWrite(f,ch, ft) => 
               val f2: ContWrite[cw.El,cw.R] => Option[(cw.El,Future[Continuated[cw.R]])] = 
                               { (cont) =>
                                 tryLocked(f(ContWrite(f,ch,ft)), cont, priority, "write") map {
                                       case (a,future) =>
                                            (a,unlockAfter(future,cont,priority,"write"))
                                 }
                               }
                               ContWrite(f2,ch,ft)
           case sk@Skip(f,ft) => val f3: Skip[sk.R] => Option[Future[Continuated[sk.R]]] = { 
                                cont =>
                                  tryLocked(f(Skip(f,ft)),cont, priority, "skip") map {
                                      unlockAfter(_,cont,priority,"skip")
                                  }
                                }
                                Skip(f3,ft)
           case dn@Done(_,_) => dn
           case Never => Never 
      }
  }

  
  @inline
  private[this] def tryLocked[X](body: => Option[X], cont: FlowContinuated[A], priority: Int, dstr: String):Option[X] =
       if (tryLock()) {
           try {
             body match {
               case None => if (mustUnlock(dstr,cont.flowTermination)) {
                               waiters.put(cont,priority)
                            }
                            None
               case sx@Some(x) => sx
             }
           }catch{
             case ex: Throwable => cont.flowTermination.doThrow(ex)
             None
           }
        } else {
           toWaiters(cont,priority)
           None
        }
            

  @inline
  private[this] def unlockAfter(f:Future[Continuated[A]], cont: FlowContinuated[A], priority: Int, dstr: String): Future[Continuated[A]] =
    f.transform(
         next => { if (mustUnlock(dstr,cont.flowTermination)) {
                         makeLocked(next, priority)
                   } else Never 
                 },
         ex =>   { mustUnlock( dstr, cont.flowTermination); ex }
    )

  private[this] def toWaiters(cont:Continuated[A],priority:Int):Unit=
  {
   this.synchronized {
     waiters.put(cont, priority) 
   }
   if (!lockFlag.get()) {
      // possible, when we call waiters.put locked, but then in other thread it was 
      // unlocked and queue cleaned before waiters modify one.
      sendWaits()
   }
  }


  private[this] def isLocked: Boolean = lockFlag.get();

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

  private[this] def sendWaits(): Unit =
  {
   // concurrent structure fpr priority queue
   var skips = List[WaitRecord[Continuated[A]]]()
   var nSend = 0
   this synchronized {
     while(waiters.nonEmpty && !lockFlag.get()) {
         waiters.take match {
           case Some(wr) =>
              nSend = nSend + 1
              wr.value match {
                case sk@Skip(_,_) => 
                                  skips = wr.asInstanceOf[WaitRecord[Continuated[A]]]::skips
                case _ =>
                        processor ! wr.value
              }
           case None => //  do nothibg.
         }
     }
   }
   if (!lockFlag.get) {
       //planIdle
       //TODO: plan instead direct send.
       for(wr <- skips) {
             (processor.ask(wr.value)(10 seconds)).foreach(x =>
                        waiters.put(x.asInstanceOf[Continuated[A]], wr.priority)
             )
       }
     }
   }

  // false when unlocked, true otherwise.
  private[this] val lockFlag: AtomicBoolean = new AtomicBoolean(false)

  val waiters: WaitPriorityQueue = new WaitPriorityQueue()
  val idleWaiters: ConcurrentLinkedQueue[Continuated[A]] = new ConcurrentLinkedQueue()

  val processor = api.continuatedProcessorRef

  implicit val executionContext: ExecutionContext = api.executionContext
  

}




