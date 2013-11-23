package example

import gopher._
import gopher.channels._
import gopher.channels.Naive._
import scala.concurrent._
import scala.concurrent.duration._

import org.scalatest._

/*
 * code from go tutorial: http://tour.golang.org/#66
* 
*/

object Fibonaccy {

  import scala.concurrent.ExecutionContext.Implicits.global
  
  def fibonacci(c: OChannel[Long], quit: IChannel[Int]): Unit = {
    var (x,y) = (0L,1L)
     for(s <- select) {
      val z = x 
      s match {
        case `c` <~ `z` => 
                      x = y; 
                      y = z+y;
        case `quit` ~> (x: Int) =>
            // Console.println("quit")
             s.shutdown()
      }
     }
  }
  
  def run(n:Int, acceptor: Long => Unit ): Unit =
  {
    val c = makeChannel[Long](1);
    val quit = makeChannel[Int](1);
    val r = go {
      for (i <- 1 to n) {
        acceptor(c?)
      }
      quit <~ 0
    }
    fibonacci(c,quit)
  }
  
  
  
}

class FibonaccySuite extends FunSuite
{
  
  test("fibonaccy must be processed up to 50") {
    var last:Long = 0;
    Fibonaccy.run(50, last = _ )
    assert(last != 0)
  }

}