package example

import org.scalatest._
import gopher._
import gopher.channels._
import gopher.channels.Naive._
import scala.concurrent.ExecutionContext.Implicits._

object FibonaccyAsync {

  
  def fibonacci(ch: OChannel[Long], quit: IChannel[Int]): Unit = {
    var (x,y) = (0L,1L)
    /*
    makeTie.forever.writing(ch){ 
                  val z = x
                  x = y
                  y = z + y
                  y
       }.reading(quit) {x =>
                       tie.shutdown               
       }.go
       * 
       */
  }
  
  def run(n:Int, acceptor: Long => Unit ): Unit =
  {
    val c = makeChannel[Long](1);
    val quit = makeChannel[Int](1);
    
    makeTie.zip(1 to 10, c) { (i,x) => 
                              { Console.print("%d, %d".format(i,x)); true }
    } `then` writing(quit)( x => 1)
    
    //c.read(1 to 10) then quit
    
    /*
    c.read{
      accept
    }.loop(1 to 10).map(quit.write(0)).go
    * 
    */
    /*
    for (i <- 1 to n;
          v <- c.read;
          x <- quit.write(0)
         ) { accept(v) }
    */  
    fibonacci(c,quit)
  }
  
  
  
}

class FibonaccyAsyncSuite extends FunSuite
{
  
  test("async fibonaccy must be processed up to 50") {
    pending
    /*var last:Long = 0;
    FibonaccyAsync.run(50, last = _)
    assert(last != 0)
    * 
    */
  }

}

