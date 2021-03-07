package gopher.channels

import cps._
import gopher._
import munit._

import scala.concurrent.{Channel=>_,_}
import scala.language.postfixOps

import cps.monads.FutureAsyncMonad

class FoldSelectSuite extends FunSuite
{


  import scala.concurrent.ExecutionContext.Implicits.global
  given Gopher[Future] = SharedGopherAPI.apply[Future]()
    

  test("fold-over-selector with changed read") {
    implicit val printCode = cps.macroFlags.PrintCode

    val in = makeChannel[Int]()
    val out = makeChannel[Int]()
    var r0 = IndexedSeq[Int]()

    // dotty bug,
    val generator = async {
      select.fold(in){ (ch,s) =>
        s.select{
          case p: ch.read =>
            r0 = r0 :+ p
            out.write(p)
            ch.filter{ _ % p != 0 }
        }
      }
    }

    //generator.failed.foreach{ _.printStackTrace() }
    val writer = async {
      for(i <- 2 to Int.MaxValue) {
        in.write(i)
      }
    }

    val read = async {
      for(i <- 1 to 100) yield {
        val x = out.read
        x
      }
    }

    read map (r => assert(r(18) == 67 && r.last == 541) )
    
  }
  
  
  /*
  test("fold-over-selector with swap read") {

    val in1 = makeChannel[Int]()
    val in2 = makeChannel[Int]()
    val quit = makeChannel[Boolean]()

    val generator = async {
      select.fold((in1,in2,0)){ case ((in1,in2,n),s) =>
        s select {
          case x:in1.read =>
            if (x >= 100) {
              s.done((in1, in2, n))
            } else {
              (in2, in1, n + x)
            }
          case x:in2.read =>
            (in2,in1,n-x)
        }
      }
    }

    in1.awriteAll(1 to 101)

    //val r = Await.result(generator, 1 second)

    // 0 + 1 - 2 + 3 - 4 + 5 - 6 ... +99 - 100 + 101
    //       - 1   2  -2   3 - 3     +50 - 50
    generator.map(r => assert(r._3 == -50))

  }
  */
    

}


