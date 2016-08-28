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
   
    val in = makeChannel[Int]()
    val out = makeChannel[Int]()
    val generator = go {
      select.fold(in){ (ch,s) =>
        s match {
          case p:ch.read => out.write(p)
                            ch.filter(_ % p == 0)
        }
      }
    }
    in.awriteAll(1 to 100)
    val read = go {
      for(i <- 1 to 10) yield {
         val p = out.read
         System.err.println(s"received:${p}")
         p
      }
    }
    val r = Await.result(read,1 second)
    System.err.println(r)
    pending
 }

 test("fold-over-selector with swap read") {

    val in1 = makeChannel[Int]()
    val in2 = makeChannel[Int]()
    val out = makeChannel[Int]()

    val generator = go {
      select.fold((in1,in2,0)){ case ((ch1,ch2,n),s) =>
        s match {
          case x:in1.read => (in2,in1,n+x)
          case x:in2.read => (in2,in1,n-x)
        }
      }
    }

    in1.awriteAll(1 to 100)

    pending
 }

}
