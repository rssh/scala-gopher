package gopher.channels

import gopher._
import akka.actor._
import scala.concurrent._
import java.util.concurrent.atomic.AtomicBoolean


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
               // lazy is a workarround for https://issues.scala-lang.org/browse/SI-6278
               lazy val f1: (cr.El, ContRead[cr.El,cr.R]) => Option[Future[Continuated[cr.R]]]  = { 
                             (a,cont) =>
                             if (tryLock()) {
                                try {
                                  f(a, ContRead(f, ch, ft) ) match {
                                    case None => 
                                      if (mustUnlock("read", cont.flwt)) {
                                         // leave one in the same queue.
                                         waiters.put(cont,priority)
                                      }
                                      None 
                                    case Some(future) =>  
                                      Some(future.transform( 
                                         next => { 
                                                   if (mustUnlock("read-2",cont.flwt)) {
                                                     makeLocked(next, priority)
                                                   } else {
                                                     Never
                                                   } 
                                                 },
                                         ex => { mustUnlock("read-3",cont.flwt); ex }
                                      ))
                                  }
                                } catch {
                                   case ex: Throwable => ft.doThrow(ex)
                                   None
                                }
                             } else {
                               // return to waiters.
                               toWaiters(cont,priority)
                               None
                             }
                           }
               ContRead(f1,ch, ft)
           case cw@ContWrite(f,ch, ft) => 
               lazy val f2: ContWrite[cw.El,cw.R] => Option[(cw.El,Future[Continuated[cw.R]])] = 
                               { (cont) =>
                                  if (tryLock()) {
                                   try {
                                     f(ContWrite(f,ch,ft)) match {
                                       case None => if (mustUnlock("write",cont.flwt)) {
                                                      waiters.put(cont,priority)
                                                    }
                                                    None
                                       case Some((a,future)) =>
                                             Some((a,future.transform(
                                                    next => { if (mustUnlock("write-2",cont.flwt)) {
                                                                makeLocked(next, priority)
                                                              } else {
                                                                Never
                                                              }
                                                            },
                                                    ex => { mustUnlock("write-3",cont.flwt); ex }
                                                 ))               )
                                                    
                                     }
                                   }catch{
                                     case ex: Throwable => ft.doThrow(ex)
                                     None
                                   }
                                  } else {
                                    toWaiters(cont,priority)
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
                                  if (unlock("skip")) {
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
                               toWaiters(cont, priority) 
                               None
                             }
                           }
                           Skip(f3,ft)
           case dn@Done(_,ft) => val f4: Skip[dn.R] => Option[Future[Continuated[dn.R]]] = {
                                 cont => 
                                 unlock("Done"); // we don't care about result.
                                 Some(Promise successful dn future)
                              }
                              Skip(f4,ft)
           case Never => Never // TODO: make never locked (?)
      }
  }

  private[this] def toWaiters(cont:Continuated[A],priority:Int):Unit=
  {
   waiters.put(cont, priority) 
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
     while(waiters.nonEmpty && !lockFlag.get()) {
         waiters.take match {
           case Some(wr) =>
                        processor!wr.value
           case None => //  do nothibg.
         }
     }
  }

  // false when unlocked, true otherwise.
  private[this] val lockFlag: AtomicBoolean = new AtomicBoolean(false)

  val waiters: WaitPriorityQueue = new WaitPriorityQueue()

  val processor = api.continuatedProcessorRef

  implicit val executionContext: ExecutionContext = api.executionContext
  

}




