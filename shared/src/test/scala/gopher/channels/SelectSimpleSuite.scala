package gopher.channels


import munit._
import scala.language.postfixOps
import scala.concurrent._
import scala.concurrent.duration._

import cps._
import gopher._
import cps.monads.FutureAsyncMonad

class SelectSimpleSuite extends FunSuite 
{

  import scala.concurrent.ExecutionContext.Implicits.global
  given Gopher[Future] = SharedGopherAPI.apply[Future]()


  test("simple select in a loop") {
      val ch = makeChannel[Int]()
      var sum = 0
      val loop = async {
        var done = false
        while(!done) {
          select {
            case x: ch.read => 
                sum = sum+x
                if (x > 100) {
                  done = true
                } 
          }
        }
      }
      ch.awriteAll(1 to 200)

      async {
        await(loop)
        assert( sum == (1 to 101).sum)
      }
  }


}
