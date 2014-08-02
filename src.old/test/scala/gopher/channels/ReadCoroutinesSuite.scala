package gopher.channels

import gopher._
import gopher.channels.Naive._

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
  
  def integers:InputOutputChannelBase[Int] =
  {
    val y = makeChannel[Int]()
    var count = 0
    go {
      while(true) {
        y <~ count
        count = count + 1;
      }
    }
    y
  }
  
  val resume = integers
  
  def generateInteger: Int =
    resume ?
  
  
}


class ReadCoroutinesSuite extends FunSuite {

  import ReadCoroutines._
  
  test("get few numbers from generarator") {
    val x0 = generateInteger
    assert(x0 == 0)
    val x1 = generateInteger
    assert(x1 == 1)
    val x2 = generateInteger
    assert(x2 == 2)
  }
  
  
}
