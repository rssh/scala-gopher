package gopher.channels

import gopher._
import gopher.channels._
import gopher.tags._

import org.scalatest._

import scala.language._
import scala.concurrent._
import scala.concurrent.duration._

class UnbufferedSelectSuite extends FunSuite 
{

   import scala.concurrent.ExecutionContext.Implicits.global

  
   test("write without read must block ")  {
     import gopherApi._
     val channel1 = makeChannel[Int](0)
     val w1 = channel1.awrite(1)

     assert(!w1.isCompleted)

     val r1 = channel1.aread

     Await.ready(r1, 10 seconds)

     assert(w1.isCompleted)
     assert(r1.isCompleted)

     val rd = Await.result(r1, 10 seconds)

     assert(rd==1)
   }

   test("fold over selector with one-direction flow")  {
    import gopherApi._
    for(i <- 1 to 100) {
     val ch = makeChannel[Int](0)
     val quit = Promise[Boolean]()
     val r = select.afold(0){ (x,s) =>
               s match {
                 case a:ch.read =>  x+a
                 case q:Boolean if (q==quit.future.read) => CurrentFlowTermination.exit(x)
               }
             }
     ch.awriteAll(1 to 10) onComplete { _ => quit success true }
     val sum = Await.result(r, 1 second)
     assert(sum==(1 to 10).sum)
    }
   }


   lazy val gopherApi = CommonTestObjects.gopherApi
   
}
