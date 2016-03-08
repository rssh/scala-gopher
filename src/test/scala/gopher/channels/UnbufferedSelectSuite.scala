package gopher.channels

import gopher._
import gopher.channels._
import gopher.tags._


import scala.language._
import scala.concurrent._
import scala.concurrent.duration._

import org.scalatest._
import org.scalatest.concurrent._

class UnbufferedSelectSuite extends FunSuite with AsyncAssertions
{

   import scala.concurrent.ExecutionContext.Implicits.global

  
   test("write without read must block ")  {
     import gopherApi._
     for(i <- 0 until 100) {
       val channel1 = makeChannel[Int](0)
       val w1 = channel1.awrite(1)

       assert(!w1.isCompleted)

       val r1 = channel1.aread

       Await.ready(w1, 10 seconds)
       Await.ready(r1, 10 seconds)

       assert(w1.isCompleted)
       assert(r1.isCompleted)

       val rd = Await.result(r1, 10 seconds)
       assert(rd==1)
     }

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

   test("append for finite unbuffered stream") {
      val w = new Waiter
      val ch1 = gopherApi.makeChannel[Int](0)
      val ch2 = gopherApi.makeChannel[Int](0)
      val appended = ch1 append ch2
      var sum = 0
      var prev = 0
      var monotonic = true
      val f = go { for(s <- appended) {
                     // bug in compiler 2.11.7
                     //w{assert(prev < s)}
                     //if (prev >= s) w{assert(false)}
                     if (prev >= s) monotonic=false
                     prev = s
                     sum += s
                 }  }
      val a1 = ch1.awriteAll(1 to 10)
      val a2 = ch2.awriteAll((1 to 10)map(_*100))
      // it works, but for buffered channeld onComplete can be scheduled before. So, <= instead ==
      a1.onComplete{ case _ => { w{assert(sum == 55)};  ch1.close(); w.dismiss() } }
      a2.onComplete{ case _ => { w{assert(sum == 5555)}; w{assert(monotonic)}; w.dismiss() } }
      w.await(timeout(10 seconds), dismissals(2))
      assert(sum==5555)
      assert(monotonic)
   }

   lazy val gopherApi = CommonTestObjects.gopherApi
   
}
