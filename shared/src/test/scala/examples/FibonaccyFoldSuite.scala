package example

import gopher._
import cps._
import munit._

import scala.concurrent._
import scala.language._

import cps.monads.FutureAsyncMonad 
import scala.concurrent.ExecutionContext.Implicits.global


/*
 * code from go tutorial: http://tour.golang.org/#66 but with fold instead foreach
* 
*/

object FibonaccyFold {

  given Gopher[Future] = SharedGopherAPI.apply[Future]()

  
  def fibonacci(c: WriteChannel[Future,Long], quit: ReadChannel[Future,Int]): Future[(Long,Long)] = async {
     select.fold((0L,1L)) { case (x,y) =>
      select{
        case wx: c.write if wx == x  =>  (y, x+y)
        case q: quit.read =>
                   SelectFold.Done((x,y))
      }
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

class FibonaccyFoldSuite extends FunSuite
{
 
  
  test("fibonaccy must be processed up to 50") {
    FibonaccyFold.run(50, _ => () ).map(last => assert(last._2 != 0))
  }

}
