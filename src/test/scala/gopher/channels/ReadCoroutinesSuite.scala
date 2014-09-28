package gopher.channels

import gopher._
import gopher.channels._
import scala.concurrent._
import scala.concurrent.duration._

import org.scalatest._

import scala.concurrent.ExecutionContext.Implicits.global

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
  
  lazy val integers:IOChannel[Int] =
  {
    val y = gopherApi.makeChannel[Int]()
    @volatile var count = 0
    go {
      while(true) {
        y <~ count
        count = count + 1;
      }
    }
    y
  }
  
  def gopherApi = CommonTestObjects.gopherApi
  
}


class ReadCoroutinesSuite extends FunSuite {

  import ReadCoroutines._
  import language.postfixOps
  
  test("get few numbers from generarator") {
   val p = go {
      val x0 = (integers ?)
      assert(x0 == 0)
      val x1 = (integers ?)
      assert(x1 == 1)
      val x2 = (integers ?)
      assert(x2 == 2)
   }
   Await.ready(p, 10 seconds)
  }
  
  
}
