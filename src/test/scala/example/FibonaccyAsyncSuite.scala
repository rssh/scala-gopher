package example

import org.scalatest._
import gopher._
import gopher.channels._
import akka.actor._
import gopher.tags._
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
        Console.print("%d, %d".format(i,x))
        last = x
    } flatMap { x => quit.awrite(1) } 
    */
    c.zip(1 to n).map{ case (i,x) =>
        Console.print("%d, %d".format(i,x))
        last = x
        (i,x)
    }.atake(n) flatMap { x => quit.awrite(1) }
    
    fibonacci(c,quit)
    acceptor(last)

  }
  

  lazy val actorSystem = ActorSystem.create("system")
  lazy val gopherApi = GopherAPIExtension(actorSystem)
  
}


class FibonaccyAsyncSuite extends FunSuite
{
  
  test("async fibonaccy must be processed up to 50", Now) {
    pending
    // zip is not implemented
    var last:Long = 0;
    FibonaccyAsync.run(50, { last = _ } )
    assert(last != 0)
  }

}

