package gopher.channels


import gopher._
import gopher.channels._
import gopher.tags._

import org.scalatest._

import scala.language._
import scala.concurrent._
import scala.concurrent.duration._


class FoldSelectSuite extends FunSuite
{

 lazy val gopherApi = CommonTestObjects.gopherApi
 import gopherApi._

 import scala.concurrent.ExecutionContext.Implicits.global

 test("fold-over-selector with changed read") {
  //for(i <- 1 to 10000) {
    val in = makeChannel[Int]()
    val out = makeChannel[Int]()
    var r0 = IndexedSeq[Int]()
    val generator = go {
      select.fold(in){ (ch,s) =>
        s match {
          case p:ch.read =>
                            r0 = r0 :+ p
                            out.write(p)
                            ch.filter{ _ % p != 0 }
        }
      }
    }
    generator.failed.foreach{ _.printStackTrace() }
    //in.awriteAll(2 to Int.MaxValue)
    go {
      for(i <- 2 to Int.MaxValue) {
         in.write(i)
      }
    }

    val read = go {
      for(i <- 1 to 100) yield {
        val x = out.read
        x
      }
    }

   //val read = scala.async.Async.async(scala.async.Async.await((1 to 100).mapAsync(i=>out.aread)))
   //val read = (1 to 100).mapAsync(i=>out.aread)

    //val r = Await.result(read,1 second)
    val r = Await.result(read,2 seconds)
    if (r.last != 541 || r(18)!=67 ) {
      System.err.println(s"r0=$r0")
      System.err.println(s"r1=$r")
    }
    //assert(r.last === 29)
    //assert(r(0) === 2)
    //assert(r(2) === 3)
    assert(r(18) === 67)
    assert(r.last === 541)
  //}
 }

 test("fold-over-selector with swap read") {

    val in1 = makeChannel[Int]()
    val in2 = makeChannel[Int]()
    val quit = makeChannel[Boolean]()

    val generator = go {
      select.fold((in1,in2,0)){ case ((in1,in2,n),s) =>
        s match {
          case x:in1.read =>
                          if (x >= 100) {
                            select.exit((in1, in2, n))
                          } else {
                            (in2, in1, n + x)
                          }
          case x:in2.read =>
                          (in2,in1,n-x)
        }
      }
    }


    in1.awriteAll(1 to 101)

    val r = Await.result(generator, 1 second)

    // 0 + 1 - 2 + 3 - 4 + 5 - 6 ... +99 - 100 + 101
    //       - 1   2  -2   3 - 3     +50 - 50
    assert(r._3 == - 50)

 }

}
