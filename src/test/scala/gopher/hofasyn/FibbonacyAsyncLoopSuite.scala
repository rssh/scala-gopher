package gopher.hofasyn

import gopher._
import gopher.channels._
import scala.language._
import scala.concurrent._
import scala.concurrent.duration._
import scala.async.Async._

import org.scalatest._
import gopher.tags._


/*
* code from go tutorial: http://tour.golang.org/#66
* 
*/
object FibonaccyL {

  import scala.concurrent.ExecutionContext.Implicits.global
  
  def fibonacci(c: Output[Long], quit: Input[Int]): Future[Unit] =
  go {
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
    go {
      for(i <-1 to n) {
         val xLLFind = c.read
         //Console.println(s"received: ${i}, ${xLLFind}")
         acceptor(xLLFind)
      }
      //System.err.println("sending quit")
      quit <~ 0
    }

    fibonacci(c,quit)
  }
  
  def gopherApi = CommonTestObjects.gopherApi 
  
}

class FibonaccyAsyncLoopSuite extends FunSuite
{
  
  test("fibonaccy must be processed up to 50") {
    @volatile var last:Long = 0;
    Await.ready( FibonaccyL.run(50, last = _ ), 10 seconds )
    assert(last != 0)
  }

}
