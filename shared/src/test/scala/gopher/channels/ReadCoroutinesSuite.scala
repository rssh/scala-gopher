package gopher.channels

import gopher._
import cps._
import munit._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Channel=>_,_}
import scala.concurrent.duration._

import cps.monads.FutureAsyncMonad

/*
 * Go analog:
 * 
 * 
  func integers () chan int {
    yield := make (chan int);
    count := 0;
    go func () {
        for {
            yield <- count;
            count++;
        }
    } ();
    return yield
   }
   ...
   resume := integers();
   func generateInteger () int {
      return <-resume
   }

   generateInteger() => 0
   generateInteger() => 1
   generateInteger() => 2
  
   * 
   */
object ReadCoroutines {

  given Gopher[Future] = SharedGopherAPI.apply[Future]()

  lazy val integers:Channel[Future,Int,Int] = {
    val y = makeChannel[Int]()
    @volatile var count = 0
    async {
      while(true) {
        y.write(count)
        count = count + 1;
      }
    }
    y
  }
  
  
  
}


class ReadCoroutinesSuite extends FunSuite {

  import ReadCoroutines._

  import language.postfixOps
  
  test("get few numbers from generarator") {
   val p = async {
      val x0 = (integers ?)
      assert(x0 == 0)
      val x1 = (integers ?)
      assert(x1 == 1)
      val x2 = (integers ?)
      assert(x2 == 2)
   }
  }
  
  
}
