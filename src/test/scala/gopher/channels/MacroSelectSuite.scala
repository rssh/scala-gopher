package gopher.channels

import gopher._

import org.scalatest._

import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global

class MacroSelectSuite extends FunSuite 
{

   test("select emulation with macroses")  {
     
     val channel = makeChannel[Int](100)
     
     
     go {
       for( i <- 1 to 1000) 
         channel <~ i 
     }
     
     var sum = 0;
     val consumer = go {
       for(s <- select) {
          s match {
             case `channel` ~> (i:Int) =>
                     //System.err.println("received:"+i)
                     sum = sum + i
                     if (i==1000)  s.shutdown()
          }
       }
       sum
     }

     Await.ready(consumer, 5.second)

     val xsum = (1 to 1000).sum
     assert(xsum == sum)
     
     
   }

   
}
