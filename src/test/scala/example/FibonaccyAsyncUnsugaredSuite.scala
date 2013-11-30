package example

import gopher._
import gopher.channels._
import gopher.channels.Naive._
import scala.async.Async._
import scala.concurrent._
import scala.concurrent.duration._
import org.scalatest._
import ch.qos.logback.classic._
import ch.qos.logback.core._
import ExecutionContext.Implicits.global

import tags._

class FibonaccyAsyncUnsugaredSuite extends FunSuite {

  
  object Fibonaccy {
 
  def fibonacci(c: OChannel[Long], quit: IChannel[Int]): Unit = {
    // bug - compiler bug, locted in context. 
    @volatile var (x,y) = (0L,1L)
    System.err.println(s"initial assignment, x=$x, y=$y")
    val tie = makeTie("generation")
    tie.logger.setLevel(ch.qos.logback.classic.Level.TRACE)
    tie.logger.error("A1")
    tie.logger.trace("A2")
    tie.addWriteAction(c,
      new WriteAction[Long] {
        override def apply(in: WriteActionInput[Long]) =
         Some(async {
                tie.logger.trace(s"write-action, x=$x, y=$y")
                val z = x
                //await{c <~* z}
                //System.err.println(s"after-await-in-write-action, z=$z")
                x = y
                y = z + y
                WriteActionOutput(Some(z),true)
              })
      }
    )
    tie.addReadAction(quit,
       new ReadAction[Int] {
         def apply(in:ReadActionInput[Int]) =
             Some(async { 
                   System.err.println(s"read-action")
                   in.tie.shutdown
                   ReadActionOutput(false) 
             })
       }
    )    
    tie.start
  }
  
  def run(n:Int, acceptor: Long => Unit ): Unit =
  {
    val c = makeChannel[Long](1,"c");
    c.logger.setLevel(ch.qos.logback.classic.Level.TRACE)
    val quit = makeChannel[Int]();
    
    val consumer = c.readZipped(1 to n) {
      (i,n) => System.out.println(s"received:${i}:${n}");
      acceptor(n)
    }.next.addWriteAction(quit,
        new PlainWriteAction[Int] {
          override def plainApply(in: WriteActionInput[Int]): WriteActionOutput[Int] = {
            System.err.println("write in quit");
            in.tie.shutdown
            WriteActionOutput(Some(1),false)         
          } 
       }
    )
          
    fibonacci(c,quit)

    Await.ready(consumer.shutdownFuture, 10 seconds)

  }
  

  }
 
 test("fibonaccy must be processed up to 50", Now) {
    var last:Long = 0;
    Fibonaccy.run(50, last = _ )
    assert(last != 0)
  }
  
}

  

