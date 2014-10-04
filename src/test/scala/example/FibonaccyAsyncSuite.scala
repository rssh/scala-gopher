package example

import org.scalatest._
import gopher._
import gopher.channels._
import akka.actor._
import gopher.tags._
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._



object FibonaccyAsync {

  def fibonacci(ch: Output[Long], quit: Input[Int]): Unit = {
    var (x,y) = (0L,1L)
    gopherApi.select.forever.writing(ch, y){ _ =>
                  val z = x
                  x = y
                  y = z + y
       }.reading(quit){ 
                  x => implicitly[FlowTermination[Unit]].doExit(())
       }.go
  }
  
  def run(n:Int, acceptor: Long => Unit ): Unit =
  {
    val c = gopherApi.makeChannel[Long](1);
    val quit = gopherApi.makeChannel[Int](1);
    
    var last=0L
    /*
    // error in compiler [scala-2.11.2]
    //TODO: debug to small example and send pr
    */
    /*
    c.zip(1 to n).foreach{ a =>
        val (x,i) = a
        Console.print("%d, %d\n".format(i,x))
        last = x
    } flatMap { x => quit.awrite(1) } 
    */
    val receiver = c.zip(1 to n).map{ case (x,i) =>
        // don't show, I trust you ;)
        //Console.print("%d, %d\n".format(i,x))
        last = x
        (i,x)
    }.atake(n+1) flatMap { x => quit.awrite(1) }
    
    fibonacci(c,quit)

    Await.ready(receiver, 10 seconds)

    acceptor(last)

  }
  
  lazy val gopherApi = channels.CommonTestObjects.gopherApi
  
}


class FibonaccyAsyncSuite extends FunSuite
{
  
  test("async fibonaccy must be processed up to 50") {
    var last:Long = 0;
    FibonaccyAsync.run(50, { last = _ } )
    assert(last != 0)
  }

}

