package gopher.channels

import cps._
import cps.monads.FutureAsyncMonad
import gopher._
import munit._

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util._

import gopher.util.Debug

import java.util.logging.{Level => LogLevel}

class DuppedChannelsMultipleSuite extends FunSuite  {

  import scala.concurrent.ExecutionContext.Implicits.global
  given gopherApi: Gopher[Future] = SharedGopherAPI.apply[Future]()

  val inMemoryLog = new Debug.InMemoryLog()
    

  test("on closing of main stream dupped outputs also closed N times.") {
    val N = 1000
    var logIndex = 0
    gopherApi.setLogFun(Debug.inMemoryLogFun(inMemoryLog))
    for(i <- 1 to N) {
      inMemoryLog.clear()
      logIndex = i
      val ch = makeChannel[Int](1)
      gopherApi.log(LogLevel.FINE, s"created origin ch=${ch}")
      val (in1, in2) = ch.dup()
      val f1 = async{
        gopherApi.log(LogLevel.FINE, s"before ch.write(1), ch=${ch}")
        ch.write(1)
        gopherApi.log(LogLevel.FINE, s"before ch.close, ch=${ch}")
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
        case ex: Throwable =>  //: TimeoutException =>
          Debug.showTraces(20)
          println("---")
          Debug.showInMemoryLog(inMemoryLog)
          throw ex
      }
    }

  }

}


