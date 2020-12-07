package gopher.channels

import gopher._

import munit._

/* TODO: enablle after implementing select macroses.
class FibbonachySimpleTest extends FunSuite {


  import scala.concurrent.ExecutionContext.Implicits.global
  val gopherApi = SharedGopherAPI.apply[Future]()


  def fibonaccy0(c: WriteChannel[Future,Long], quit: ReadChannel[Future,Int]): Future[Unit] =
    async[Future]{
      var (x,y) = (0,1)
      var done = false
      while(!done) {
        select{
          case z: c.Write if (z == x) =>
            x = y
            y = z + y
          case q: quit.Read => 
            done = true
        }
      }
    }

  test("simple fibonnachy fun") {

  }


}
*/
