package gopher.channels

import cps._
import cps.monads.FutureAsyncMonad
import gopher._
import munit._

import scala.concurrent._
import scala.concurrent.duration._
import scala.language._
import scala.util._

class DuppedChannelsSuite extends FunSuite  {

  import scala.concurrent.ExecutionContext.Implicits.global
  given Gopher[Future] = SharedGopherAPI.apply[Future]()


  test("duped input must show two") {
    val ch = makeChannel[String]()
    val dupped = ch.dup()
    val r0 = ch.awrite("1")
    val r1 = dupped._1.aread
    val r2 = dupped._2.aread
    val r = for(v1 <- r1; v2 <- r2) yield (v1,v2)

    r map {x => 
      assert(x == ("1","1")) 
    }

  }

/*
  test("output is blocked by both inputs") {
    import CommonTestObjects.FutureWithTimeout
    val ch = gopherApi.makeChannel[Int]()
    val aw=ch.awriteAll(1 to 100)
    val (in1, in2) = ch.dup
    val at1 = in1.atake(100)
    val awt = aw.withTimeout(1 second)
    val w = recoverToSucceededIf[TimeoutException](awt)
    w.map(_ => assert(!aw.isCompleted && !at1.isCompleted)).flatMap { x =>
      in2.atake(100) map (_ => assert(aw.isCompleted))
    }
  }

  test("on closing of main stream dupped outputs also closed.") {
    val ch = gopherApi.makeChannel[Int](1)
    val (in1, in2) = ch.dup
    val f1 = go {
      ch.write(1)
      ch.close()
    }
    for{ fx <- f1
         x <- in1.aread
         r <- in1.aread.transformWith {
           case Success(u) => Future failed new IllegalStateException("Mist be closed")
           case Failure(u) => Future successful (assert(x == 1))
         }
    } yield {
      r
    }

  }
*/



}


