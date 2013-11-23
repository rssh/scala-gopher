package example

import gopher._
import gopher.channels._
import gopher.channels.Naive._
import scala.async.Async._
import scala.concurrent._
import org.scalatest._

import ExecutionContext.Implicits.global

import tags._

class FibonaccyAsyncUnsugaredSuite extends FunSuite {

  
  object Fibonaccy {

  
 
  def fibonacci(c: OChannel[Long], quit: IChannel[Int]): Unit = {
    var (x,y) = (0L,1L)
    val tie = makeTie
    tie.addWriteAction(c,
      new WriteAction[Long] {
        override def apply(in: WriteActionInput[Long]) =
         Some(async {
                val z = x
                await{c <~* z}
                x = y
                y = z + y
                WriteActionOutput(Some(y),true)
              })
      }
    )
    tie.addReadAction(quit,
       new ReadAction[Int] {
         def apply(in:ReadActionInput[Int]) =
             Some(async { 
                   in.tie.shutdown
                   ReadActionOutput(false) 
             })
       }
    )    
    tie.start
  }
  
  def run(n:Int, acceptor: Long => Unit ): Unit =
  {
    val c = makeChannel[Long]();
    val quit = makeChannel[Int]();
    
    c.readZipped(1 to n) {
      (i,n) => System.out.println(s"${i}:${n}");
      acceptor(n)
    }.next.addWriteAction(quit,
        new PlainWriteAction[Int] {
          override def plainApply(in: WriteActionInput[Int]): WriteActionOutput[Int] = {
            WriteActionOutput(Some(1),false)         
          } 
       }
    )
        
    fibonacci(c,quit)
  }
  

  }
 
 test("fibonaccy must be processed up to 50", Now) {
    var last:Long = 0;
    Fibonaccy.run(50, last = _ )
    assert(last != 0)
  }
  
}

  

