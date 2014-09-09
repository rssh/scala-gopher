package gopher.channels

import gopher._
import scala.concurrent._
import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.{LinkedList => JLinkedList}

class IdleDetector(api: GopherAPI)
{

   def put(s: Selector[_]): Unit =
   {
     selectors add SelectorRecord(0L,s)
     if (!idleDetectorActive) {
          System.err.println("schedule detect fun")
          idleDetectorActive=true
          // TODO: get from config.
          val scheduler = api.actorSystem.scheduler
          System.err.println("scheduler: "+scheduler)
          val cancelable = scheduler.schedule(
             100 milliseconds,
             500 milliseconds){
                         detect 
             }(api.executionContext)
          System.err.println("received cancelable: "+cancelable)
     }
   }

   def remove(s: Selector[_]):Unit =
   {
     val it = selectors.iterator();
     var found = false
     while(it.hasNext() && !found) {
       val sr = it.next()
       if (sr.selector eq s) {
           selectors.remove(sr)
       }
     }
   }

   private[this] def detect: Unit =
   {
    System.err.println("Idle detector:detect start");
    var q=false;
    var nonIdles = new JLinkedList[SelectorRecord]()
    while(!q) {
      val sr = selectors.poll()
      if (sr==null) {
        System.err.println("no records in selector list");
        q=true
      } else {
        val s = sr.selector
        System.err.println("processing selector "+s);
        if (!s.isCompleted) {
          if (s.isLocked) {
           nonIdles add sr
          } else {
           val nOps = s.nOperations.get
           if (nOps != sr.prevNOperations) {
              nonIdles add SelectorRecord(nOps,s)
           } else {
              s.startIdles
           }
          }
        }
      }
    }
    selectors.addAll(nonIdles)
   }



   case class SelectorRecord(
     prevNOperations: Long,
     selector: Selector[_]
   )


   val selectors = new ConcurrentLinkedQueue[SelectorRecord]()
   @volatile var idleDetectorActive = false;
}
