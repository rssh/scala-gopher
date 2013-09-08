package gopher.channels

import org.scalatest._

import gopher._
import gopher.channels.Naive.api
import scala.concurrent.ExecutionContext.Implicits.global


class AsyncSelectSuite extends FunSuite {

     test("async base select emulation")  {
     
     val channel = makeChannel[Int](100)
     
     makeTie.write(channel)(1 to 100).go
     
     //go {
     //  for( i <- 1 to 1000) 
     //    channel <~ i 
     //}
     
     var sum = 0;
     
     val consumer = makeTie.reading(channel).withTie { (t,i) =>
       sum = sum + i
       if (i==1000) t.shutdown()
     }.go
     
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

     Await.ready(consumer, 5.second)

     val xsum = (1 to 1000).sum
     assert(xsum == sum)
     
     
   }

  
}