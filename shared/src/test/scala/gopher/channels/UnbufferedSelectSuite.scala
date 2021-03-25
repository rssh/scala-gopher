package gopher.channels

import cps._
import gopher._
import munit._

import scala.language.postfixOps
import scala.concurrent._
import scala.concurrent.duration._


class UnbufferedSelectSuite extends FunSuite 
{

   import cps.monads.FutureAsyncMonad
   import scala.concurrent.ExecutionContext.Implicits.global

   given Gopher[Future] = SharedGopherAPI.apply[Future]()

  
   test("write without read must block ")  {
       val channel1 = makeChannel[Int](0)
       val w1 = channel1.awrite(1)

       assert(!w1.isCompleted)

       val r1 = channel1.aread()

       async {
          await(w1)
          await(r1)
          val rd = await(r1)
          assert(rd==1)
       }

   }


  
   test("fold over selector with one-direction flow")  {
    
     val ch = makeChannel[Int](0)
     val quit = Promise[Boolean]()
     val quitChannel = quit.future.asChannel
     val r = async {
        select.fold(0){ (x,s) =>
               s.apply{
                 case a:ch.read =>  x+a
                 case q: quitChannel.read => SelectFold.Done(x) 
               }
        }
     }
     ch.awriteAll(1 to 10) onComplete { _ => quit success true }
     async {
        val sum = await(r)
        assert(sum==(1 to 10).sum)
     }
   }

   
   test("append for finite unbuffered stream") {
      val ch1 = makeChannel[Int](0)
      val ch2 = makeChannel[Int](0)
      val appended = ch1 append ch2
      var sum = 0
      var prev = 0
      var monotonic = true
      val f = async { for(s <- appended) {
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

      async {  
        await(a1)
        while (sum < 55) {
          Time.sleep(50 milliseconds)
        } 
        assert(sum == 55)
        
        // after this - read from 2
        ch1.close() 

        await(a2)
        while(sum < 5555) {
          Time.sleep(50 milliseconds)
        }
        assert(sum == 5555)
        assert(monotonic)
      }
   }

   
}
