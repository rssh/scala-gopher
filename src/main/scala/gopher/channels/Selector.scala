package gopher.channels

import akka.actor._
import scala.concurrent._
import java.util.concurrent.atomic.AtomicBoolean


class Selector[A](api: API) extends FlowTermination[A]
{

  thisSelector =>

  def addReader[E](ch:Input[E],f: (E, ContRead[E,A]) => Option[Future[Continuated[A]]], priority:Int): Unit =
  {
   waiters.put(makeLocked(ContRead(f, ch, this), priority),priority)
  }
 
  def doThrow(ex: Throwable): Unit =
   resultPromise failure ex 

  def doExit(a:A): Unit =
  {
   resultPromise success a
  }

  def run:Future[A] =
  {
    sendWaits
    resultPromise.future
  }
  
  private def makeLocked(block: Continuated[A], priority: Int): Continuated[A] =
      block match {
           case cr@ContRead(_,ch, ft) => 
               // lazy is a workarround for https://issues.scala-lang.org/browse/SI-6278
               lazy val f1: (cr.El, ContRead[cr.El,cr.R]) => Option[Future[Continuated[cr.R]]]  = { 
                             (a,cont) =>
                             if (tryLock()) {
                                try {
                                  cont.f(a, ContRead(f1, ch, ft) )  map( r => r map {   x => 
                                  if (unlock()) {
                                     makeLocked(x, priority)
                                  } else {
                                     throw new IllegalStateException("other fiber occypied select 'lock'");
                                  }
                                                                                   }
                                                                       ) 
                                } catch {
                                   case ex: Throwable => ft.doThrow(ex)
                                                         None
                                }
                             } else {
                               makeWaitLocked(cont,priority)
                               None
                             }
                           }
               ContRead(f1,ch, ft)
           case cw@ContWrite(_,ch, ft) => 
               lazy val f2: ContWrite[cw.El,cw.R] => Option[(cw.El,Future[Continuated[cw.R]])] = 
                               { (cont) =>
                                  if (tryLock()) {
                                   try {
                                     cont.f(ContWrite(f2,ch,ft)) map{ case (el, x) =>
                                       if (unlock()) {
                                          (el, x map( r=> makeLocked(r, priority)))
                                       } else {
                                          throw new IllegalStateException("other fiber occypied select 'lock'");
                                       }
                                     }
                                   }catch{
                                     case ex: Throwable => ft.doThrow(ex)
                                                           None
                                   }
                                  } else {
                                    makeWaitLocked(cont, priority) 
                                    None
                                  }
                                }
                                ContWrite(f2,ch,ft)
           case sk@Skip(_,ft) => lazy val f3: Skip[sk.R] => Option[Future[Continuated[sk.R]]] = { 
                             cont =>
                             if (tryLock()) {
                               try {
                                cont.f(Skip(f3,ft)) map( _ map { 
                                  x => 
                                  if (unlock()) {
                                     makeLocked(x,priority);
                                  } else {
                                     throw new IllegalStateException("other fiber occypied select 'lock'");
                                  }
                                })
                               } catch {
                                case ex: Throwable => ft.doThrow(ex)
                                                      None
                               }
                             } else {
                               makeWaitLocked(cont, priority) 
                               None
                             }
                           }
                           Skip(f3,ft)
           case dn@Done(_,ft) => val f4: Skip[dn.R] => Option[Future[Continuated[dn.R]]] = {
                                 cont => 
                                 unlock(); // we don't care about result.
                                 Some(Promise successful dn future)
                              }
                              Skip(f4,ft)
           case Never => Never // TODO: make never locked (?)
      }

  def makeWaitLocked(block:Continuated[A], priority:Int): Future[Continuated[A]] =
  {
   // TODO: check for end
   val locked = makeLocked(block, priority)
   waiters.put(locked,priority)
  }

  private[this] def isLocked: Boolean = lockFlag.get();

  private[this] def tryLock(): Boolean = lockFlag.compareAndSet(false,true)

  private[this] def unlock(): Boolean =
  {
     val retval = lockFlag.compareAndSet(true,false)
     if (retval) {
        sendWaits()
     }
     retval
  }


  private[this] def sendWaits(): Unit =
  {
     while(waiters.nonEmpty && !lockFlag.get()) {
         waiters.take match {
           case Some(wr) =>
                        wr.promise.success(wr.value)
                        processor!wr.value
           case None => //  do nothibg.
         }
     }
  }

  // false when unlocked, true otherwise.
  private[this] val lockFlag: AtomicBoolean = new AtomicBoolean(false)

  private[this] val resultPromise = Promise[A]

  val waiters: WaitPriorityQueue = new WaitPriorityQueue()

  val processor = api.continuatedProcessorRef

  implicit val executionContext: ExecutionContext = api.executionContext
  

}




