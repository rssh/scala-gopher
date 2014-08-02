package gopher.channels

import org.scalatest._
import java.util.concurrent._

class SynchPrimitiveSuite extends FunSuite
{
  
  test("cyclic barriwe with more then N touchs") {
    val barrier = new CyclicBarrier(2)
    @volatile var x1a = false
    @volatile var x1b = false
    @volatile var x2a = false
    @volatile var x2b = false
    val th1 = new Thread() { 
      override def run(): Unit = {
            Thread.sleep(500)
            x1a = true
            barrier.await()
            x1b = true
      }
    }
 
    th1.setDaemon(true)
    val th2 = new Thread() {
      override def run(): Unit = {
           Thread.sleep(500)
           x2a = true
           barrier.await()
           x2b = true
      }   
    }
    th2.setDaemon(true)
    barrier.reset()
    th1.start()
    th2.start()
    barrier.await()
    val z = true
    assert(z == true)
    assert(x1a || x2a)
  }
  
  test("CountDownLatch with 1 touchs") {
    val latch = new CountDownLatch(1)
    @volatile var x1a = false
    @volatile var x1b = false
    @volatile var x2a = false
    @volatile var x2b = false
    val th1 = new Thread() { 
      override def run(): Unit = {
            Thread.sleep(500)
            x1a = true
            latch.countDown()
            x1b = true
      }
    }
 
    th1.setDaemon(true)
    val th2 = new Thread() {
      override def run(): Unit = {
           Thread.sleep(500)
           x2a = true
           latch.countDown()
           x2b = true
      }   
    }
    th2.setDaemon(true)
    th1.start()
    th2.start()
    latch.await()
    val z = true
    assert(z == true)
    assert(x1a || x2a)
  }
  

}