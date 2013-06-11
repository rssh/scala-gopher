package go.channels

import org.scalatest._

import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global

class SelectSuite extends FunSuite 
{

   test("basic select emulation")  {
     
     val channel = makeChannel[Int](100)
     
     val producer = Future {
       
       for( i <- 1 to 1000) {
         { val sc = new SelectorContext()
           sc.addOutputAction(channel,
                 () => Some(i)
               )
           sc.runOnce
         }
       }
       channel
       
     }
     
     var sum = 0;
     val consumer = Future {
       val sc = new SelectorContext()
       sc.addInputAction(channel, 
            (i: Int) => { sum = sum + i; 
                          if (i == 1000) {
                            System.err.println("shutdowned");
                            sc.shutdown()
                          }
                          System.err.println("received i:"+i)
                          true 
                        }
       )
       sc.runOnce;
       Await.ready(sc.go, 5.second)
       System.err.println("after end of sc.go, sum="+sum);
       
     }
   
  
     Await.ready(consumer, 5.second)

     val xsum = (1 to 1000).sum
     System.err.println("xsum="+xsum);
     assert(xsum == sum)
     
     
   }
  
  
}