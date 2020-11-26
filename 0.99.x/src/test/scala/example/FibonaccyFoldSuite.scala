package example

import gopher.channels._
import org.scalatest._

import scala.concurrent._
import scala.language._

/*
 * code from go tutorial: http://tour.golang.org/#66 but with fold instead foreach
* 
*/

object FibonaccyFold {

  import CommonTestObjects.gopherApi._

  import scala.concurrent.ExecutionContext.Implicits.global
  
  def fibonacci(c: Output[Long], quit: Input[Int]): Future[(Long,Long)] = 
     select.afold((0L,1L)) { case ((x,y),s) =>
      s match {
        case x: c.write =>  (y, x+y)
        case q: quit.read =>
                   select.exit((x,y))
      }
     }
  
  def run(n:Int, acceptor: Long => Unit ): Future[(Long,Long)] =
  {
    val c = makeChannel[Long](1);
    val quit = makeChannel[Int](1);
    val r = c.map{ x =>
            //Console.println(s"${x} ")
            acceptor(x)}.atake(n) flatMap ( _ => (quit awrite 0) )
    fibonacci(c,quit)
  }
  
  
}

class FibonaccyFoldSuite extends AsyncFunSuite
{
  
  test("fibonaccy must be processed up to 50") {
    FibonaccyFold.run(50, _ => () ).map(last => assert(last._2 != 0))
  }

}
