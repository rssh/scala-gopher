package gopher.channels.history

import gopher._
import scala.concurrent._
import cps.monads.FutureAsyncMonad

import munit._

class AsyncSelectSuite extends FunSuite {

  val MAX_N=100

  import scala.concurrent.ExecutionContext.Implicits.global
  given Gopher[Future] = SharedGopherAPI.apply[Future]()

  
  test("async base: channel write, select read")  {

    val channel = makeChannel[Int](10)

    channel.awriteAll(1 to MAX_N)

    var sum = 0;

    /*
    val consumer = gopherApi.select.loop.onRead(channel){ a
      sum = sum + a
      (a:Int, cont:ContRead[Int,Unit]) => sum = sum + a
        if (a < MAX_N) {
          cont
        } else {
          Done((),cont.flowTermination)
        }
    }.go
    */

    val consumer = select.loop.onRead(channel){ a =>
           sum = sum + a
           a < MAX_N
    }.runAsync()

    //val consumer = go {
    //  for(s <- select) {
    //     s match {
    //        case `channel` ~> (i:Int) =>
    //                //System.err.println("received:"+i)
    //                sum = sum + i
    //                if (i==1000)  s.shutdown()
    //     }
    //  }
    //  sum
    //}

    consumer map { x =>
      val xsum = (1 to MAX_N).sum
      assert(xsum == sum)
    }

  }


  test("async base: select write, select read")  {

    val channel = makeChannel[Int](10)

    var sum=0
    var curA=0
    val process = select.loop.
      onRead(channel){ a => sum = sum + a
          //System.err.println("received:"+a)
          a < MAX_N
      }.onWrite(channel, curA){ a =>
          curA = curA + 1
          curA < MAX_N
      }.runAsync()

    process map { _ =>
      assert(curA == MAX_N)
    }

  }

/*
  test("async base: select read, timeout action")  {

    val channel = makeChannel[Int](10)

    val consumer = channel.atake(100)

    var i = 1
    var d = 1
    val process = gopherApi.select.loop[Int].onWrite(channel) {
      cont:ContWrite[Int,Int] => i=i+1
        (i,cont)
    }.onIdle{
      cont:Skip[Int] =>
        if (i < 100) {
          d=d+1
          cont
        } else {
          Done(d,cont.flowTermination)
        }
    }.go

    for{rp <- process
        rc <- consumer } yield assert(i > 100)

  }
  */

  /*
  test("async base: catch exception in read")  {
    val ERROR_N = 10
    var lastReaded = 0
    val channel = gopherApi.makeChannel[Int](10)
    val process = gopherApi.select.loop.
      onRead(channel){
        (a:Int, cont:ContRead[Int,Unit]) => lastReaded=a
          if (a == ERROR_N) {
            throw new IllegalStateException("qqq")
          }
          cont
      }.go

    channel.awriteAll(1 to MAX_N)

    recoverToSucceededIf[IllegalStateException]{
      process
    }

  }

  test("async base: catch exception in idle")  {
    val process = gopherApi.select.loop.onIdle(
      (cont: Skip[Int]) =>
        if (true) {
          throw new IllegalStateException("qqq")
        } else cont
    ).go

    recoverToSucceededIf[IllegalStateException]{
      process
    }

  }
  */

 

}

