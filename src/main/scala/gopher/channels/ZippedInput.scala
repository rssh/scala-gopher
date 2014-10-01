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
   val ready = pairs.poll()
   if (!(ready eq null)) {
         f(ContRead(f,this,flwt)) match {
           case Some(f1) => f1(()=>ready)
           case None => Future successful Never
         }
    } else {
         readers.add(ContRead(f,this,flwt))
         val s = new State[A,B](None,None)
         inputA.cbread[C](cont => Some(gen => {s.oa = Some(gen()) 
                                                fireAttempt(s)
                                              }  )
                         , flwt)
         inputB.cbread[C](cont =>  
                                    Some(gen => {s.ob = Some(gen())
                                                 fireAttempt(s)
                                               }  )
                         , flwt)
    }

   
    def fireAttempt(s:State[A,B]):Future[Continuated[C]] = 
    {
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
                             case Some(f1) => f1(()=>pair).onComplete( api.continuatedProcessorRef ! _ )
                             case None => pairs.add(pair)
                           }
                        }
           case _ =>  /* do-nothing */
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

 case class State[A,B](var oa:Option[A], var ob:Option[B])

}


