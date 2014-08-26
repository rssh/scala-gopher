package gopher.channels

import org.scalatest._
import gopher._
import gopher.tags._
import akka.actor._
import scala.concurrent._
import scala.concurrent.duration._

class AsyncSelectSuite extends FunSuite {

     
     val MAX_N=100  

     test("async base: channel write, select read")  {
     
       
     val channel = gopherApi.makeChannel[Int](10)
     
     channel.awriteAll(1 to MAX_N)
     
     //go {
     //  for( i <- 1 to 1000) 
     //    channel <~ i 
     //}
     
     var sum = 0;
     
     val consumer = gopherApi.select.loop.onRead(channel){  
        (a:Int, cont:ContRead[Int,Unit]) => sum = sum + a
        System.err.println("received:"+a)
        if (a < MAX_N) {
           cont
        } else {
           System.err.println("sending done")
           Done((),cont.flowTermination)
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

   test("async base: select write, select read")  {

     val channel = gopherApi.makeChannel[Int](10)

     var sum=0
     var curA=0
     val process = gopherApi.select.loop.
      onRead(channel){  
        (a:Int, cont:ContRead[Int,Unit]) => sum = sum + a
        //System.err.println("received:"+a)
        if (a < MAX_N) {
           cont
        } else {
           Done((),cont.flowTermination)
        }
      }.onWrite(channel){
        cont:ContWrite[Int,Unit] => 
          curA = curA+1
          if (curA < MAX_N) {
             (curA, cont)
          } else {
             (curA,Done((),cont.flowTermination))
          }
      }.go
     
      Await.ready(process, 10000.second)

      assert(curA == MAX_N)

    }

    test("async base: select read, default action", Now)  {

       val channel = gopherApi.makeChannel[Int](10)

       val consumer = channel.atake(100)

       var i = 1
       var d = 1
       val process = gopherApi.select.loop[Int].onWrite(channel) {
                       cont:ContWrite[Int,Int] => i=i+1
                                                  (i,cont)
                     }.onIdle{
                       cont:Skip[Int] =>        
                           if (i < 100) {
                             System.err.println("skip, i="+i)
                             d=d+1
                             cont
                           } else {
                             Done(d,cont.flowTermination)
                           }
                     }.go

       Await.ready(process, 10000.second)

       assert(consumer.isCompleted)
       assert(i>100)
       System.err.println("d="+d)

    }


    val actorSystem = ActorSystem.create("system")
    val gopherApi = GopherAPIExtension(actorSystem)
  
}
