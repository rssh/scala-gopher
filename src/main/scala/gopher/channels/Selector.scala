package gopher.channels

import akka.actor._
import scala.concurrent._
import java.util.concurrent.atomic.AtomicBoolean


class Selector(processor: ActorRef)
{

  thisSelector =>

  def addReaderOnce[A](input:Input[A], f: A => Future[Unit], priority: Int ): Unit =
      processor ! makeLocked(
          ContRead( ((a:A,self:ContRead[A,Unit]) =>  f(a) map( Done(_))) , input), 
          priority)

  def addReaderForever[A](input:Input[A], f: A => Future[Unit], priority: Int ): Unit =
      processor ! makeLocked(
          ContRead( ((a:A,self:ContRead[A,Unit]) =>  f(a) map( x => self)) , input), 
          priority)

  private def makeLocked[A](block: Continuated[A], priority: Int): Continuated[A] =
      block match {
           case cr@ContRead(_,ch) => 
                           // lazy is a workarround for https://issues.scala-lang.org/browse/SI-6278
                           lazy val f1: (cr.El, ContRead[cr.El,cr.R]) => Future[Continuated[cr.R]]  = { 
                             (a,cont) =>
                             if (tryLock()) {
                                cont.f(a, ContRead(f1, ch) ) map {   x => 
                                  if (unlock()) {
                                     makeLocked(x, priority)
                                  } else {
                                     throw new IllegalStateException("other fiber occypied select 'lock'");
                                  }
                                } 
                             } else makeWaitLocked(cont, priority)
                           }
                           ContRead(f1,ch)
           case cw@ContWrite(_,ch) => 
                            lazy val f2: ContWrite[cw.El,cw.R] => Future[(Option[cw.El],Continuated[cw.R])] = 
                               { (cont) =>
                                  if (tryLock()) {
                                     cont.f(ContWrite(f2,ch)) map { case (el, x) =>
                                       if (unlock()) {
                                          (el, makeLocked(x, priority))
                                       } else {
                                          throw new IllegalStateException("other fiber occypied select 'lock'");
                                       }
                                     }
                                  } else {
                                     makeWaitLocked(cont, priority) map { x:Continuated[cw.R] => (None,x) }
                                  }
                                }
                                ContWrite(f2,ch)
           case sk@Skip(_) => lazy val f3: Skip[sk.R] => Future[Continuated[sk.R]] = { 
                             cont =>
                             if (tryLock()) {
                                cont.f(Skip(f3)) map { 
                                  x => 
                                  if (unlock()) {
                                     makeLocked(x,priority);
                                  } else {
                                     throw new IllegalStateException("other fiber occypied select 'lock'");
                                  }
                                }
                             } else makeWaitLocked(cont, priority) 
                           }
                           Skip(f3)
           case dn@Done(_) => val f4: Skip[dn.R] => Future[Continuated[dn.R]] = {
                                 cont => 
                                 unlock(); // we don't care about result.
                                 Promise successful dn future
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
                        // TODO: put into processor ?
          case None => //  do nothibg.
         }
       }
     }
     retval
  }

  // false when unlocked, true otherwise.
  private[this] val lockFlag: AtomicBoolean = new AtomicBoolean(false)

  val waiters: WaitPriorityQueue = new WaitPriorityQueue();
  implicit val executionContext: ExecutionContext = ???

}


// TODO: (low level) let's lock flag keep the id of fiber...


