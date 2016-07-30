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

   
   test("test async operations inside map")  {
     val channel = gopherApi.makeChannel[Int](100)
     channel.awriteAll(1 to 100)
     val fresult = go{
       for(i <- 1 to 100) yield channel.read
     }
     val result = Await.result(fresult, 5.second)
     assert(result(0)==1)
     assert(result(1)==2)
     assert(result(99)==100)
   }


   test("write to channel in options.foreach")  {
     val channel = gopherApi.makeChannel[Int](100)
     val optChannel = Some(channel)
     val f1 = go {
       optChannel.foreach{ _.write(1) }
     }
     val f2 = go {
       optChannel.map{ _.read }
     }
     val r2 = Await.result(f2, 5.second)
     assert(r2.isDefined)
     assert(r2 === Some(1) )
   }

   test("nested option foreach")  {
     val a:Option[Int] = Some(1)
     val b:Option[Int] = Some(3)
     val channel = gopherApi.makeChannel[Int](10)
     val fin = go {
       for (xa <- a;
            xb <- b) channel.write(xa+xb)
     } 
     val fout = channel.aread
     val r = Await.result(fout, 5.second)
     assert(r == 4)
   }

   test("option flatMap")  {
     val channel = gopherApi.makeChannel[Int](10)
     val map = Map(1->Map(2->channel))
     val fout = go {
       for (x <- map.get(1);
            ch <- x.get(2)) yield ch.read
     }
     val fin = channel.awrite(1)
     val r = Await.result(fout, 5.second)
     assert(r == Some(1))
   }

   test("channels foreach ") {
      val channels = gopherApi.makeChannel[Channel[Int]](10)
      val fin = go {
        for(ch <- channels) {
           ch.awrite(1)
        }
      }
      val ch = gopherApi.makeChannel[Int](10)
      channels.awrite(ch)
      val fout = ch.aread
      val r = Await.result(fout, 5.second)
      assert(r == 1)
   }

   lazy val gopherApi = CommonTestObjects.gopherApi
   
}
