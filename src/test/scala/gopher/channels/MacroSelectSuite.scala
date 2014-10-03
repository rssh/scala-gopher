package gopher.channels

import gopher._
import gopher.channels._

import org.scalatest._

import scala.concurrent._
import scala.concurrent.duration._

class MacroSelectSuite extends FunSuite 
{

   import scala.concurrent.ExecutionContext.Implicits.global

  
   test("select emulation with macroses")  {
     
     val channel = gopherApi.makeChannel[Int](100)
     
     go {
       var i = 1
       while(i <= 1000) {
         channel <~ i 
         i+=1
       }
       //TODO: implement for in goas preprocessor to async
       //for( i <- 1 to 1000) 
       //  channel <~ i 
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

   
   test("select with run-once")  {
     import gopherApi._
     val channel1 = makeChannel[Int](100)
     val channel2 = makeChannel[Int](100)

     val g = go {
      var nWrites=0;
      for(s <- select.once) 
        s match {
          case x: channel1.write if (x==1) => { {}; nWrites = nWrites + 1 }
          case x: channel2.write if (x==1) => { {}; nWrites = nWrites + 1 }
        }

      @volatile var nReads=0;
      for(s <- select.once) 
        s match {
          case  x: channel1.read => { {}; nReads = nReads + 1 }
          case  x: channel2.read => { {}; nReads = nReads + 1 }
        }

     }
 
     Await.ready(g, 10 seconds)

   }

   lazy val gopherApi = CommonTestObjects.gopherApi
   
}
