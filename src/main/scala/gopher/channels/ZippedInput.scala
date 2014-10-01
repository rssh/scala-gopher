package gopher.channels

import scala.concurrent._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import scala.util._
import java.util.concurrent.ConcurrentLinkedQueue
import gopher._


class ZippedInput[A,B](override val api: GopherAPI, inputA: Input[A], inputB: Input[B]) extends Input[(A,B)]
{
     
 import ZippedInput._

 val pairs = new ConcurrentLinkedQueue[(A,B)]()
 val readers = new ConcurrentLinkedQueue[ContRead[(A,B),_]]


 def  cbread[C](f: (ContRead[(A,B),C] => Option[(()=>(A,B)) => Future[Continuated[C]]]), flwt: FlowTermination[C] ): Unit =
 {
   if (!pairs.isEmpty) {
         implicit val ec = api.executionContext
         f(ContRead(f,this,flwt)) match {
           case Some(f1) => 
                        val ready = pairs.poll();
                        if (! (ready eq null) ) {
                          f1(()=>ready) onComplete {
                            case Success(x) => api.continuatedProcessorRef ! x
                            case Failure(ex) => flwt.doThrow(ex)
                          }
                        } else {
                          // unfortunelly, somebody eat our pair between !empry and poll()
                          // if we receive f1, than let submit next reader, which will apply f1.
                          //  unfortunelly, this can cause ver rare block 'select' until we
                          //  end the f1
                          // TODO: send skip instead.
                          cbread[C]( cont => Some(gen => f1(gen)) , flwt )
                        }
           case None => /* do nothing */
         }
    } else {
         readers.add(ContRead(f,this,flwt))
         val s = new State[A,B]
         inputA.cbread[C](cont => Some(gen => {
                                               val toFire = s.synchronized{
                                                              s.oa=Some(gen())
                                                              s.ob.isDefined
                                                            }
                                               fireAttempt(toFire, s)
                                              }  )
                         , flwt)
         inputB.cbread[C](cont =>  
                                    Some(gen => {
                                                 val toFire = s.synchronized{
                                                                s.ob = Some(gen())
                                                                s.oa.isDefined
                                                              }
                                                fireAttempt(toFire,s)
                                               }  )
                         , flwt)
    }

   
    def fireAttempt(toFire: Boolean, s:State[A,B]):Future[Continuated[C]] = 
    {
      if (toFire) {
        s match {
           case State(Some(a),Some(b)) => 
                        val pair = (a,b)
                        val cont = readers.poll().asInstanceOf[ContRead[(A,B),({type R})#R]] 
                                                  // existencial type not allow cont.function(cont)
                        if (cont eq null) {
                           pairs.add(pair)
                        } else {
                           implicit val ec = api.executionContext
                           cont.function(cont) match {
                             case Some(f1) => {
                                      f1(()=>pair).onComplete{ 
                                        case Success(x) =>
                                                api.continuatedProcessorRef ! x 
                                        case Failure(ex) =>
                                                cont.flowTermination.doThrow(ex)
                                      }
                                  }
                             case None => {
                                      pairs.add(pair)
                                  }
                           }
                        }
           case _ =>  throw new IllegalStateException("Impossible: fully-filled state is a precondition");
         }
       }
          // always return never, since real continuated we passed after f1 from readers queue was executed.
          // note, that we can't return it direct here, becouse type of readers head continuation can be
          // other than C, as in next scenario:
          // 1. Reader R1 call cbread and start to collect (a1,b1) (readers <- R1)
          // 2. Reader R2 call cbread and start to collect (a2,b2) (readers <- R2)
          // 3. (a1,b1) collected, but R1 is locked. (pairs <- (a1,a2), readers -> drop R1)
          //  in such case fireAttempt for R1 will process R2 (wich can have different C in FlowTermination[C])
       Future successful Never
     }


 }

    
}

object ZippedInput
{

 // can't be case class: compiler error when annotating variables.
 // TODO: submit bug to compiler
 class State[A,B]
 {
   @volatile var oa:Option[A] = None
   @volatile var ob:Option[B] = None
 }

 object State
 {
   def unapply[A,B](s:State[A,B]) = Some((s.oa,s.ob))
 }

}


