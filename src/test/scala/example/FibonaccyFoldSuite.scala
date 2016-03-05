package example

import gopher._
import gopher.channels._
import scala.language._
import scala.concurrent._
import scala.concurrent.duration._

import org.scalatest._
import gopher.tags._

/*
 * code from go tutorial: http://tour.golang.org/#66 but with fold instead foreach
* 
*/

object FibonaccyFold {

  import scala.concurrent.ExecutionContext.Implicits.global
  
  def fibonacci(c: Output[Long], quit: Input[Int]): Future[(Long,Long)] = 
     gopherApi.select.afold((0L,1L)) { case ((x,y),s) =>
      s match {
        case x: c.write =>  (y, x+y)
        case q: quit.read =>
                 implicitly[FlowTermination[(Long,Long)]].doExit((x,y))
      }
     }
  
  def run(n:Int, acceptor: Long => Unit ): Future[(Long,Long)] =
  {
    val c = gopherApi.makeChannel[Long](1);
    val quit = gopherApi.makeChannel[Int](1);
    val r = c.map{ x =>
            //Console.println(s"${x} ")
            acceptor(x)}.atake(n) flatMap ( _ => (quit awrite 0) )
    fibonacci(c,quit)
  }
  
  def gopherApi = CommonTestObjects.gopherApi 
  
}

class FibonaccyFoldSuite extends FunSuite
{
  
  test("fibonaccy must be processed up to 50") {
    val last = Await.result( FibonaccyFold.run(50, _ => () ), 10 seconds )._2
    assert(last != 0)
  }

}
