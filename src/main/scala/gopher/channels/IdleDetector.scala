package gopher.channels

import gopher._
import scala.concurrent._
import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.{LinkedList => JLinkedList}
import com.typesafe.config._

class IdleDetector(api: GopherAPI)
{

   def put(s: Selector[_]): Unit =
   {
     selectors add SelectorRecord(0L,s)
     if (!idleDetectorActive) {
          idleDetectorActive=true
          val scheduler = api.actorSystem.scheduler
          val tick = idleDetectionTick
          val cancelable = scheduler.schedule(
             tick/2 milliseconds,
             tick milliseconds){
                         detect 
             }(api.executionContext)
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
    var q=false;
    var nexts = new JLinkedList[SelectorRecord]()
    while(!q) {
      val sr = selectors.poll()
      if (sr==null) {
        q=true
      } else {
        val s = sr.selector
        if (!s.isCompleted) {
          System.err.println("idel-detector:detect, !completed s = "+s);
          var next = sr
          if (!s.isLocked) {
           val nOps = s.nOperations.get
           if (nOps != sr.prevNOperations) {
              next = SelectorRecord(nOps,s)
           } else {
              s.startIdles
           }
          } else {
            System.err.println("idel-detector:detect: is-locked");
          }
          nexts add next
        }
      }
    }
    selectors.addAll(nexts)
   }

   /**
    * tick duration of idle detection in ms.
    */
   def idleDetectionTick = try {
                    api.config.getInt("idle-detection-tick")
                  } catch {
                    case ex: ConfigException.Missing => 100
                  }


   case class SelectorRecord(
     prevNOperations: Long,
     selector: Selector[_]
   )


   val selectors = new ConcurrentLinkedQueue[SelectorRecord]()
   @volatile var idleDetectorActive = false;
}
