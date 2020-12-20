package gopher.channels

import gopher._
import cps._
import cps.monads.FutureAsyncMonad
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import java.util.concurrent.atomic._



import munit._

class SelectGroupTest extends FunSuite {

  import scala.concurrent.ExecutionContext.Implicits.global
  given Gopher[Future] = SharedGopherAPI.apply[Future]()


  def exclusiveProcess(name: String, ch1: Channel[Future,Int, Int], ch2: Channel[Future,Int, Int])(implicit loc:munit.Location) = {
    test( s"select group should not run few async processes in parallel ($name)" ){

     

      val group = new SelectGroup[Future,Unit](summon[Gopher[Future]])

      val inW1 = new AtomicInteger(0)
      val inW2 = new AtomicInteger(0)
      val commonCounter = new AtomicInteger(0)
      val myFirst = new AtomicBoolean(false)
      

      group.onReadAsync(ch1){ (a) => 
          val x1 = inW1.incrementAndGet()
          commonCounter.incrementAndGet()
          Time.asleep(1 second).map{_ => 
            assert(inW2.get() == 0)
            val x2 = inW1.incrementAndGet()
          }
      }.onReadAsync(ch2){ (a) =>
          val x1 = inW2.incrementAndGet()
          commonCounter.incrementAndGet()
          Time.asleep(1 second).map{ _ =>
            assert(inW1.get() == 0) 
            val x2 = inW2.incrementAndGet()
          }
      }

      ch1.awrite(1)
      ch2.awrite(2)

      async{
        group.run()
        assert(commonCounter.get()==1)
      }

    }
  }

  exclusiveProcess("unbuffered-unbuffered", makeChannel[Int](), makeChannel[Int]() )
  exclusiveProcess("buffered-buffered", makeChannel[Int](10), makeChannel[Int](10) )
  exclusiveProcess("promise-promise", makeOnceChannel[Int](), makeOnceChannel[Int]() )
  exclusiveProcess("buffered-unbuffered", makeChannel[Int](10), makeChannel[Int]() )


}
