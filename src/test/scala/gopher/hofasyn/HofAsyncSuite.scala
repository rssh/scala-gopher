package gopher.channels

import gopher._
import gopher.channels._
import gopher.tags._

import org.scalatest._

import scala.language._
import scala.concurrent._
import scala.concurrent.duration._

class HofAsyncSuite extends FunSuite 
{

   import scala.concurrent.ExecutionContext.Implicits.global

  
   test("select emulation with macroses")  {
     
     val channel = gopherApi.makeChannel[Int](100)
     
     go {
       for( i <- 1 to 1000) 
         channel <~ i 
     }
     
     var sum = 0;
     val consumer = go {
       for(s <- gopherApi.select.forever) {
          s match {
             case i: channel.read =>
                     //System.err.println("received:"+i)
                     sum = sum + i
                     if (i==1000)  
                        implicitly[FlowTermination[Unit]].doExit(())
          }
       }
       sum
     }

     Await.ready(consumer, 5.second)

     val xsum = (1 to 1000).sum
     assert(xsum == sum)
     
   }

   


   lazy val gopherApi = CommonTestObjects.gopherApi
   
}
