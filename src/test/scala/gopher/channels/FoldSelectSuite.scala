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
   
    val in = makeChannel[Int](10)
    val out = makeChannel[Int](10)
    val generator = go {
      select.fold(in){ (ch,s) =>
        s match {
          case p:ch.read => 
                            System.err.println(s"inFold.1, read ${p}, writing one to output")
                            out.write(p)
                            System.err.println(s"inFold.2, after write")
                            //ch.filter(_ % p != 0)
                            val x = ch.filter{ x =>
                                val r = (x%p != 0)
                                System.err.println(s"eratosphen: filtered ${x} by ${p}, r=${r}")
                                r
                            }
                            System.err.println(s"inFold.3 created channel ${x}")
                            x
        }
      }
    }
    generator.onFailure{
      case ex: Throwable => ex.printStackTrace()
    }
    //in.awriteAll(2 to 100)
    in.awriteAllDebug(2 to 100)
    val read = go {
      for(i <- 1 to 10) yield {
         System.err.println(s"in read loop: before out.read, step=${i}:")
         val p = out.read
         System.err.println(s"in read loop: received:${p}")
         p
      }
    }
    Thread.sleep(1000)

    val r = Await.result(read,1 second)
    System.err.println(r)
    pending
 }

 test("fold-over-selector with swap read") {
    pending

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
