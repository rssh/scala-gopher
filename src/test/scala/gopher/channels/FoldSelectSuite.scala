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
  for(i <- 1 to 100) {
    //pending // we debug next now
    val in = makeChannel[Int]()
    val out = makeChannel[Int]()
    val generator = go {
      select.fold(in){ (ch,s) =>
        s match {
          case p:ch.read =>
                            out.write(p)
                            ch.filter(_ % p != 0)
        }
      }
    }
    generator.failed.foreach{ _.printStackTrace() }
    in.awriteAll(2 to Int.MaxValue)
    //in.awriteAllDebug(2 to 100)
    val read = go {
      for(i <- 1 to 10) yield out.read
    }
    //Thread.sleep(1000)

    val r = Await.result(read,1 second)
    assert(r.last === 29)
  }
 }

 test("fold-over-selector with swap read") {
    //pending

    val in1 = makeChannel[Int]()
    val in2 = makeChannel[Int]()
    val quit = makeChannel[Boolean]()

    val generator = go {
      select.fold((in1,in2,0)){ case ((in1,in2,n),s) =>
        s match {
          case x:in1.read =>
                          if (x >= 100) {
                            select.exit((in1, in2, n))
                          } else
                            (in2,in1,n+x)
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
