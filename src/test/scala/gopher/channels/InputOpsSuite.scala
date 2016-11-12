package gopher.channels

import gopher._
import gopher.channels._
import gopher.tags._
import scala.language._
import scala.concurrent._
import scala.concurrent.duration._
import scala.util._

import org.scalatest._
import org.scalatest.concurrent._

import scala.concurrent.ExecutionContext.Implicits.global

class InputOpsSuite extends FunSuite with Waiters {

  
  test("map operation for input") {
      val w = new Waiter
      val ch = gopherApi.makeChannel[String]()
      ch.awriteAll(List("AAA","123","1234","12345"))
      val mappedCh = ch map (_.reverse)
      val r = mappedCh.atake(4) map { l =>
                w{ assert(l(0) == "AAA") }
                w{ assert(l(1) == "321") }
                w{ assert(l(2) == "4321") }
                w{ assert(l(3) == "54321") }
                w.dismiss()
              }
      w.await(timeout(10 seconds))
  }
  
  test("filter operation for input") {
      val w = new Waiter
      val ch = gopherApi.makeChannel[String]()
      ch.awriteAll(List("qqq", "AAA","123","1234","12345"))
      val filteredCh = ch filter (_.contains("A"))
      filteredCh.aread map { x => w{ assert(x == "AAA") } } onComplete{ case Success(x) => w.dismiss()
                                                                        case Failure(ex) => w(throw ex) 
                                                                      }
      w.await(timeout(10 seconds))
  }


  test("zip operation for two simple inputs") {
      val w = new Waiter
      val ch1 = gopherApi.makeChannel[String]()
      ch1.awriteAll(List("qqq", "AAA","123","1234","12345"))
      val ch2 = gopherApi.makeChannel[Int]()
      ch2.awriteAll(List(1, 2, 3, 4, 5, 6))
      val zipped = ch1 zip ch2
      val r1 = Await.result(zipped.aread, 10 seconds)
      assert(r1 == ("qqq", 1))
      val r2 = Await.result(zipped.aread, 10 seconds)
      assert(r2 == ("AAA", 2))
      val r3 = Await.result(zipped.aread, 10 seconds)
      assert(r3 == ("123", 3))
      val r4 = Await.result(zipped.aread, 10 seconds)
      assert(r4 == ("1234", 4))
      val r5 = Await.result(zipped.aread, 10 seconds)
      assert(r5 == ("12345", 5))
  }

  test("zip operation from two finite channels") {
      val ch1 = Input.asInput(List(1,2),gopherApi)
      val ch2 = Input.asInput(List(1,2,3,4,5,6),gopherApi)
      val zipped = ch1 zip ch2
      val r1 = Await.result(zipped.aread, 10 seconds)
      assert(r1 == (1, 1))
      val r2 = Await.result(zipped.aread, 10 seconds)
      assert(r2 == (2, 2))
      intercept[ChannelClosedException] {
        val r3 = Await.result(zipped.aread, 10 seconds)
      }
  }

  test("take from zip") {
      val ch1 = Input.asInput(List(1,2,3,4,5),gopherApi)
      val ch2 = Input.asInput(List(1,2,3,4,5,6),gopherApi)
      val zipped = ch1 zip ch2
      val at = zipped.atake(5)
      var ar = Await.result(at, 10 seconds)
      assert(ar(0)==(1,1))
      assert(ar(4)==(5,5))
  }

  test("taking from iterator-input") {
      val ch1 = Input.asInput(List(1,2,3,4,5),gopherApi)
      val at = ch1.atake(5)
      var ar = Await.result(at, 10 seconds)
      assert(ar(4)==5)
  }

  test("zip with self will no dup channels, but generate (odd, even) pairs. It's a feature, not a bug") {
      val ch = gopherApi.makeChannel[Int]()
      val zipped = ch zip ch
      val ar1 = zipped.aread
      ch.awriteAll(List(1,2,3,4,5,6,7,8))
      assert( Set((1,2),(2,1)) contains Await.result(ar1, 10 seconds)  )
      val ar2 = zipped.aread
      assert( Set((3,4),(4,3)) contains Await.result(ar2, 10 seconds)  )
      val ar3 = zipped.aread
      assert( Set((5,6),(6,5)) contains Await.result(ar3, 10 seconds) )
  }

  test("reading from Q1|Q2") {

      val ch1 = gopherApi.makeChannel[Int]()
      val ch2 = gopherApi.makeChannel[Int]()

      val ar1 = (ch1 | ch2).aread
      ch1.awrite(1)

      val r1 = Await.result(ar1, 10 seconds)
      assert(r1==1)
      
      val ar2 = (ch1 | ch2).aread
      ch2.awrite(2)
      val r2 = Await.result(ar2, 10 seconds)
      assert(r2==2)

  }

  test("simultanuos reading from Q1|Q2") {

      val w = new Waiter()

      val ch1 = gopherApi.makeChannel[Int]()
      val ch2 = gopherApi.makeChannel[Int]()

      val ar1 = (ch1 | ch2).aread
      val ar2 = (ch1 | ch2).aread

      ch1.awrite(1)
      ch2.awrite(2)

      val r1 = Await.result(ar1, 10 seconds)
      val r2 = Await.result(ar2, 10 seconds)

      if (r1 == 1) {
        assert(r2 == 2)
      } else {
        assert(r2 == 1)
      }

      val ar3 = (ch1 | ch2).aread
      intercept[TimeoutException] {
         val r3 = Await.result(ar3, 300 milliseconds)
      }

  }

  test("reflexive or  Q|Q") {
      val ch = gopherApi.makeChannel[Int]()
      val aw1 = ch.awrite(1)
      val ar1 = (ch | ch).aread
      val r1 = Await.result(ar1, 10 seconds)
      assert(r1==1)
      val ar2 = (ch | ch).aread
      intercept[TimeoutException] {
         val r2_1 = Await.result(ar2, 300 milliseconds)
      }
      val aw2 = ch.awrite(3)
      val r2 = Await.result(ar2, 10 seconds)
      assert(r2==3)
  }

  test("two items read from Q1|Q2") {
      val ch1 = gopherApi.makeChannel[Int]()
      val ch2 = gopherApi.makeChannel[Int]()
      val aw1 = ch1.awrite(1)
      val aw2 = ch2.awrite(2)
      val chOr = (ch1 | ch2)
      val ar1 = chOr.aread
      val ar2 = chOr.aread
      val r1 = Await.result(ar1, 10 seconds)
      val r2 = Await.result(ar2, 10 seconds)
      assert( ((r1,r2)==(1,2)) ||((r1,r2)==(2,1)) )
  }

  test("atake read from Q1|Q2") {
      val ch1 = gopherApi.makeChannel[Int]()
      val ch2 = gopherApi.makeChannel[Int]()

      val aw1 = ch1.awriteAll(1 to 2)
      val aw2 = ch2.awriteAll(1 to 2)
      val at = (ch1 | ch2).atake(4)
      val r = Await.result(at, 10 seconds)
  }

  test("awrite/take ") {
      val ch = gopherApi.makeChannel[Int]()
      val aw = ch.awriteAll(1 to 100)
      val at = ch.atake(100)
      val r = Await.result(at, 10 seconds)
  }

  test("Input foreach on closed stream must do nothing ") {
      val ch = gopherApi.makeChannel[Int]()
      @volatile var flg = false
      val f = go { for(s <- ch) { 
                     flg = true  
                 } }
      ch.close()
      val r = Await.result(f, 10 seconds)
      assert(!flg)
  }

  test("Input foreach on stream with 'N' elements inside must run N times ") {
      val w = new Waiter
      val ch = gopherApi.makeChannel[Int]()
      @volatile var count = 0
      val f = go { for(s <- ch) { 
                     count += 1
                 } }
      val ar = ch.awriteAll(1 to 10)
      ar.onComplete{ case _ => { ch.close(); w.dismiss() } }
      f.onComplete{ case _ => w{ assert(count == 10) }; w.dismiss() }
      // Too many awaits.
      w.await(timeout(10 seconds), dismissals(2))
  }

  test("Input afold on stream with 'N' elements inside ") {
      val ch = gopherApi.makeChannel[Int]()
      val f = ch.afold(0)((s,e)=>s+1)
      val ar = ch.awriteAll(1 to 10)
      ar.onComplete{ case _ => ch.close() }
      val r = Await.result(f,10 seconds) 
      assert(r==10)
  }

  test("forech with mapped closed stream") {
    def one(i:Int) = {
      val w = new Waiter
      val ch = gopherApi.makeChannel[Int]() 
      val mapped = ch map (_ * 2)
      @volatile var count = 0
      val f = go { for(s <- mapped) { 
                     //  error in compiler
                     //assert((s % 2) == 0)
                     if ((s%2)!=0) {
                       throw new IllegalStateException("numbers in mapped channel must be odd")
                     }
                     count += 1
                 }              }
      val ar = ch.awriteAll(1 to 10)
      ar.onComplete{ case _ => { ch.close(); w.dismiss() } }
      f.onComplete{ case _ => { w{assert(count == 10)}; w.dismiss() } }
      w.await(timeout(10 seconds), dismissals(2))
    }
    for(i <- 1 to 10) one(i)
  }

  test("forech with filtered closed stream") {
      val w = new Waiter
      val ch = gopherApi.makeChannel[Int]() 
      val filtered = ch filter (_ %2 == 0)
      @volatile var count = 0
      val f = go { for(s <- filtered) { 
                      count += 1
                 }                    }
      val ar = ch.awriteAll(1 to 10)
      ar.onComplete{ case _ => { ch.close(); w.dismiss() } }
      f.onComplete{ case _ => { w{assert(count == 5)}; w.dismiss() } }
      w.await(timeout(10 seconds), dismissals(2))
  }

/*
  test("channel fold with async operation inside") {
      val ch1 = gopherApi.makeChannel[Int](10) 
      val ch2 = gopherApi.makeChannel[Int](10) 
      val fs = go {
        val sum = ch1.fold(0){ (s,n) =>
                    val n1 = ch2.read
                    //s+(n1+n2) -- stack overflow in 2.11.8 compiler. TODO: submit bug
                    s+(n+n1)
                  }
        sum
      }
      go {
       ch1.writeAll(1 to 10)
       ch2.writeAll(1 to 10)
       ch1.close()
      }
      val r = Await.result(fs, 10 seconds)
      assert(r==110)
  }
*/


  test("append for finite stream") {
      val w = new Waiter
      val ch1 = gopherApi.makeChannel[Int](10) 
      val ch2 = gopherApi.makeChannel[Int](10) 
      val appended = ch1 append ch2
      var sum = 0
      var prev = 0
      var monotonic = true
      val f = go { for(s <- appended) {
                     // bug in compiler 2.11.7
                     //w{assert(prev < s)}
                     //if (prev >= s) w{assert(false)}
                     if (prev >= s) monotonic=false
                     prev = s
                     sum += s  
                 }  }
      val a1 = ch1.awriteAll(1 to 10) 
      val a2 = ch2.awriteAll((1 to 10)map(_*100)) 
      // it works, but for buffered channeld onComplete can be scheduled before. So, <= instead ==
      a1.onComplete{ case _ => { w{assert(sum <= 55)};  ch1.close(); w.dismiss() } }
      a2.onComplete{ case _ => { w{assert(sum <= 5555)}; w{assert(monotonic)}; w.dismiss() } }
      w.await(timeout(10 seconds), dismissals(2))
      assert(sum<=5555)
      assert(monotonic)
  }
         
  test("append for empty stream") {
      val w = new Waiter
      val ch1 = gopherApi.makeChannel[Int]() 
      val ch2 = gopherApi.makeChannel[Int]() 
      val appended = ch1 append ch2
      val f = appended.atake(10).map(_.sum)
      f.onComplete{ case Success(x) => { w{assert(x==55)}; w.dismiss() } 
                    case Failure(_) => { w{assert(false)}; w.dismiss() }
                  }
      ch1.close()
      val a2 = ch2.awriteAll(1 to 10) 
      w.await(timeout(10 seconds), dismissals(1))
  }

  def gopherApi = CommonTestObjects.gopherApi

  
}
