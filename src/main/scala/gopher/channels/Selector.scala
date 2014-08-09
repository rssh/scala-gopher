package gopher.channels

import akka.actor._
import scala.concurrent._
import java.util.concurrent.atomic.AtomicBoolean


class Selector(processor: ActorRef, api: API)
{

  thisSelector =>

  private def makeLocked[A](block: Continuated[A], priority: Int): Continuated[A] =
      block match {
           case cr@ContRead(_,ch) => 
               // lazy is a workarround for https://issues.scala-lang.org/browse/SI-6278
               lazy val f1: (cr.El, ContRead[cr.El,cr.R]) => Option[Future[Continuated[cr.R]]]  = { 
                             (a,cont) =>
                             if (tryLock()) {
                                cont.f(a, ContRead(f1, ch) ) map( r => r map {   x => 
                                  if (unlock()) {
                                     makeLocked(x, priority)
                                  } else {
                                     throw new IllegalStateException("other fiber occypied select 'lock'");
                                  }
                                }) 
                             } else {
                               makeWaitLocked(cont,priority)
                               None
                             }
                           }
               ContRead(f1,ch)
           case cw@ContWrite(_,ch) => 
               lazy val f2: ContWrite[cw.El,cw.R] => Option[(cw.El,Future[Continuated[cw.R]])] = 
                               { (cont) =>
                                  if (tryLock()) {
                                     cont.f(ContWrite(f2,ch)) map{ case (el, x) =>
                                       if (unlock()) {
                                          (el, x map( r=> makeLocked(r, priority)))
                                       } else {
                                          throw new IllegalStateException("other fiber occypied select 'lock'");
                                       }
                                     }
                                  } else {
                                    makeWaitLocked(cont, priority) 
                                    None
                                  }
                                }
                                ContWrite(f2,ch)
           case sk@Skip(_) => lazy val f3: Skip[sk.R] => Option[Future[Continuated[sk.R]]] = { 
                             cont =>
                             if (tryLock()) {
                                cont.f(Skip(f3)) map( _ map { 
                                  x => 
                                  if (unlock()) {
                                     makeLocked(x,priority);
                                  } else {
                                     throw new IllegalStateException("other fiber occypied select 'lock'");
                                  }
                                })
                             } else {
                               makeWaitLocked(cont, priority) 
                               None
                             }
                           }
                           Skip(f3)
           case dn@Done(_) => val f4: Skip[dn.R] => Option[Future[Continuated[dn.R]]] = {
                                 cont => 
                                 unlock(); // we don't care about result.
                                 Some(Promise successful dn future)
                              }
                              Skip(f4)
           case Never => Never // TODO: make never locked (?)
      }

  def makeWaitLocked[A](block:Continuated[A], priority:Int): Future[Continuated[A]] =
  {
   val locked = makeLocked(block, priority)
   waiters.put(locked,priority)
  }

  private[this] def isLocked: Boolean = lockFlag.get();

  private[this] def tryLock(): Boolean = lockFlag.compareAndSet(false,true)

  private[this] def unlock(): Boolean =
  {
     val retval = lockFlag.compareAndSet(true,false)
     if (retval) {
       while(waiters.nonEmpty && !lockFlag.get()) {
         waiters.take match {
          case Some(wr) =>
                        wr.promise.success(wr.value)
                        processor!wr.value
          case None => //  do nothibg.
         }
       }
     }
     retval
  }

  // false when unlocked, true otherwise.
  private[this] val lockFlag: AtomicBoolean = new AtomicBoolean(false)

  val waiters: WaitPriorityQueue = new WaitPriorityQueue();
  implicit val executionContext: ExecutionContext = api.executionContext

}




