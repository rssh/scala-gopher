package gopher.channels

import scala.concurrent._
import cps._
import cps.monads.FutureAsyncMonad
import gopher._

import munit._

class FibbonachySimpleTest extends FunSuite {


  import scala.concurrent.ExecutionContext.Implicits.global
  given Gopher[Future] = SharedGopherAPI.apply[Future]()


  def fibonaccy0(c: WriteChannel[Future,Long], quit: ReadChannel[Future,Int]): Future[Unit] =
    async[Future]{
      var (x,y) = (0L,1L)
      var done = false
      while(!done) {
        // TODO: add select group to given
        select.group[Unit]().writing(c, x){ x0 =>
                       x=y
                       y=x0+y
                    }
                   .reading(quit){ v =>
                       done = true
                   }
                   .run
      }
    }

  def fibonaccy1(c: WriteChannel[Future,Long], quit: ReadChannel[Future,Int]): Future[Unit] =
    async[Future]{
      var (x,y) = (0L,1L)
      var done = false
      while(!done) {
        select{
          case z: c.write if (z == x) =>
            x = z
            y = z + y
          case q: quit.read => 
            done = true
        }
      }
    }


  def run(starter: (WriteChannel[Future,Long], ReadChannel[Future,Int])=> Future[Unit],
          acceptor: Long => Unit, n:Int): Future[Unit] = {
    val fib = makeChannel[Long]()
    val q = makeChannel[Int]()
    val start = starter(fib,q)
    async{
      for( i <- 1 to n) {
        val x = fib.read
        acceptor(x)
      }
      q <~ 1
    }
  }

  test("simple fibonnachy fun (no macroses)") {
    @volatile var last: Long = 0L
    async{
      await(run(fibonaccy0, last = _, 40))
      assert(last != 0)
    }
  }

  test("fibonnachy fun (select macros)") {
    @volatile var last: Long = 0L
    async{
      await(run(fibonaccy0, last = _, 40))
      assert(last != 0)
    }
  }


}
