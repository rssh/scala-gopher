package gopher.channels

import cps._
import cps.monads.FutureAsyncMonad
import gopher._
import munit._

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util._

class DuppedChannelsMultipleSuite extends FunSuite  {

  import scala.concurrent.ExecutionContext.Implicits.global
  given gopherApi: Gopher[Future] = SharedGopherAPI.apply[Future]()

  val inMemoryLog = new java.util.concurrent.ConcurrentLinkedQueue[(Int, Long, String, Throwable)]()
    

  test("on closing of main stream dupped outputs also closed N times.") {
    val N = 1000
    var logIndex = 0
    gopherApi.setLogFun( (level,msg, ex) => inMemoryLog.add((logIndex, Thread.currentThread().getId(), msg,ex)) )
    for(i <- 1 to N) {
      logIndex = i
      val ch = makeChannel[Int](1)
      val (in1, in2) = ch.dup()
      val f1 = async{
        ch.write(1)
        ch.close()
      }
      val f = for{ fx <- f1
         x <- in1.aread()
         r <- in1.aread().transformWith {
           case Success(u) => 
               Future failed new IllegalStateException("Mist be closed")
           case Failure(u) => 
               Future successful (assert(x == 1))
         }
      } yield {
        r
      }
      try {
        val r = Await.result(f, 30 seconds);
      }catch{
        case ex: TimeoutException =>
          showTraces(20)
          println("---")
          showInMemoryLog()
          throw ex
      }
    }

  }

  def showTraces(maxTracesToShow: Int): Unit = {
    val traces = Thread.getAllStackTraces();
    val it = traces.entrySet().iterator()
    while(it.hasNext()) {
      val e = it.next();
      println(e.getKey());
      val elements = e.getValue()
      var sti = 0
      var wasPark = false
      while(sti < elements.length && sti < maxTracesToShow && !wasPark) {
        val st = elements(sti)
        println(" "*10 + st)
        sti = sti + 1;
        wasPark = (st.getMethodName == "park")
      }
    }
  } 

  def showInMemoryLog(): Unit = {
    while(!inMemoryLog.isEmpty) {
      val r = inMemoryLog.poll()
      if (r != null) {
        println(r)
      }
    }
  }


}


