package gopher.channels

import gopher._
import org.scalatest._
import org.scalatest.concurrent._

import scala.concurrent._
import scala.concurrent.duration._
import scala.language._
import scala.util._

class DuppedChannelsAsyncSuite extends AsyncFunSuite  {


  test("duped input must show two") {
    val ch = gopherApi.makeChannel[String]()
    val dupped = ch.dup
    ch.awrite("1")
    val r1 = dupped._1.aread
    val r2 = dupped._2.aread
    val r = for(v1 <- r1; v2 <- r2) yield (v1,v2)

    r map (x => assert(x === ("1","1")) )

  }

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



  def gopherApi = CommonTestObjects.gopherApi


}


class DuppedChannelsSuite extends FunSuite with ScalaFutures with Waiters {

  import scala.concurrent.ExecutionContext.Implicits.global


  test("on closing of main stream dupped outputs also closed.") {
      val ch = gopherApi.makeChannel[Int](1)
      val (in1, in2) = ch.dup
      val f1 = go {
          ch.write(1) 
          ch.close()
      }
      Await.ready(f1, 1 second) 
      val w = new Waiter
      in1.aread map { x =>  w(assert(x==1)); w.dismiss() } onComplete {
                           case Failure(ex) => w( throw ex )
                           case Success(_) =>
                                     in1.aread.failed.foreach{ ex => w(assert(ex.isInstanceOf[ChannelClosedException]));
                                                           w.dismiss() 
                                 }
      }
      w.await(timeout(10 seconds),Dismissals(2))
  }

  def gopherApi = CommonTestObjects.gopherApi

  
}
