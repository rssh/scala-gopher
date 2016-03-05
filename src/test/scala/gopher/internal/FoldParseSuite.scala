package gopher.internal

import gopher._
import gopher.channels._
import scala.language._
import scala.concurrent._
import scala.concurrent.duration._

import org.scalatest._
import gopher.tags._


object FoldData {

  import scala.concurrent.ExecutionContext.Implicits.global
  
  def foldWithCase(c: Output[Long], quit: Input[Int]): Future[(Long,Long,Long)] = 
     gopherApi.select.afold((0L,1L,2L)) {
       case ((x,y,z), s) => s match {
                             case v: c.write if (v==x) =>
                                        (y,z,y+z)
                             case q: quit.read =>
                                       CurrentFlowTermination.exit((x,y,z))
                           }
     }
  
  def foldWithCaseWithoutGuard(c: Output[Long], quit: Input[Int]): Future[(Long,Long,Long)] = 
     gopherApi.select.afold((0L,1L,2L)) {
       case ((x,y,z), s) => s match {
                             case x: c.write =>
                                        (y,z,y+z)
                             case q: quit.read =>
                                       CurrentFlowTermination.exit((x,y,z))
                           }
     }
  

  def foldWithoutCase(c: Output[Long], quit: Input[Int]): Future[Long] = 
     gopherApi.select.afold(1L) { (x,s) =>
        s match {
          case v: c.write if (v==x) => x+1
          case q: quit.read => CurrentFlowTermination.exit(x)
        }
     } 

  def run1(n:Int, acceptor: Long => Unit ): Future[(Long,Long,Long)] =
  {
    val c = gopherApi.makeChannel[Long](1);
    val quit = gopherApi.makeChannel[Int](1);
    val r = go {
      // for loop in go with async insied yet not supported
      var i = 1
      while(i <= n) {
        val x: Long = (c ?)
        //Console.println(s"received: ${i}, ${x}")
        acceptor(x)
        i += 1
      }
      quit <~ 0
    }

    foldWithCase(c,quit)

    
  }
  

  def gopherApi = CommonTestObjects.gopherApi 
  
}

class FoldParseSuite extends FunSuite
{
  
  test("fold must be parsed") {
    @volatile var last:Long = 0;
    Await.ready( FoldData.run1(50, last = _ ), 10 seconds )
    assert(last != 0)
  }

  test("case var must shadow text") {
  }

}
