package gopher.channels

import org.scalatest._
import gopher._
import akka.actor._
import scala.concurrent._
import scala.concurrent.duration._
//import tags._

class AsyncSelectSuite extends FunSuite {

     

     test("async base select emulation")  {
     
     val MAX_N=100  
       
     val channel = gopherApi.makeChannel[Int](10)
     
     channel.awriteAll(1 to MAX_N)
     
     //go {
     //  for( i <- 1 to 1000) 
     //    channel <~ i 
     //}
     
     var sum = 0;
     
     val consumer = gopherApi.select.loop.onRead(channel){  
        (a:Int, cont:ContRead[Int,Unit]) => sum = sum + a
        if (a < MAX_N) {
           cont
        } else {
           Done((),cont.flwt)
        }
     }.go
     
     //val consumer = makeTie.reading(channel).withTie { (t,i) =>
     //  sum = sum + i
     //  if (i==1000) t.shutdown()
     //}.go
     
     //val consumer = go {
     //  for(s <- select) {
     //     s match {
     //        case `channel` ~> (i:Int) =>
     //                //System.err.println("received:"+i)
     //                sum = sum + i
     //                if (i==1000)  s.shutdown()
     //     }
     //  }
     //  sum
     //}

     Await.ready(consumer, 1000.second)

     val xsum = (1 to MAX_N).sum
     assert(xsum == sum)
     
   }

    val actorSystem = ActorSystem.create("system")
    val gopherApi = GopherAPIExtension(actorSystem)
  
}
