package example

import gopher._
import gopher.channels._
import org.scalatest._

import scala.concurrent._
import scala.language._

/*
 * code from go tutorial: http://tour.golang.org/#66
* 
*/

object Fibonaccy {

  import scala.concurrent.ExecutionContext.Implicits.global
  
  def fibonacci(c: Output[Long], quit: Input[Int]): Future[Unit] = go {
     var (x,y) = (0L,1L)
     for(s <- gopherApi.select.forever) {
      s match {
        case z: c.write if (z == x) => 
                      x = y 
                      y = z+y
        case q: quit.read =>
                 implicitly[FlowTermination[Unit]].doExit(())
      }
     }
  }
  
  def run(n:Int, acceptor: Long => Unit ): Future[Unit] =
  {
    val c = gopherApi.makeChannel[Long](1);
    val quit = gopherApi.makeChannel[Int](1);
    val r = go {
      // for loop in go with async inside 
      for( i<- 1 to n) {
          val x: Long = (c ?)
          //Console.println(s"received: ${i}, ${x}")
          acceptor(x)
      }
      quit <~ 0
    }

    fibonacci(c,quit)
  }
  
  def gopherApi = CommonTestObjects.gopherApi 
  
}

class FibonaccySuite extends AsyncFunSuite
{
  
  test("fibonaccy must be processed up to 50") {
    var last:Long = 0;
    Fibonaccy.run(50, last = _ ) map (x => assert(last!=0))
  }

}
