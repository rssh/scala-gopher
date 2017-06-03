package gopher.channels

import gopher._
import org.scalatest._
import org.scalatest.concurrent._

import scala.concurrent._
import scala.concurrent.duration._
import scala.language._
import scala.util._

class InputOpsAsyncSuite extends AsyncFunSuite  {


  test("map operation for input") {
    val ch = gopherApi.makeChannel[String]()
    ch.awriteAll(List("AAA","123","1234","12345"))
    val mappedCh = ch map (_.reverse)
    mappedCh.atake(4) map { l =>
       assert(l(0) == "AAA" &&
              l(1) == "321" &&
              l(2) == "4321" &&
              l(3) == "54321")
    }
  }


  test("filter operation for input") {
    val ch = gopherApi.makeChannel[String]()
    ch.awriteAll(List("qqq", "AAA","123","1234","12345"))
    val filteredCh = ch filter (_.contains("A"))
    filteredCh.aread map { x => assert(x == "AAA")  }
  }


    test("zip operation for two simple inputs") {
        //val w = new Waiter
        val ch1 = gopherApi.makeChannel[String]()
        ch1.awriteAll(List("qqq", "AAA","123","1234","12345"))
        val ch2 = gopherApi.makeChannel[Int]()
        ch2.awriteAll(List(1, 2, 3, 4, 5, 6))
        val zipped = ch1 zip ch2
        for{ r1 <- zipped.aread
             _ = assert( r1 == ("qqq",1) )
             r2 <- zipped.aread
             _ = assert( r2 == ("AAA",2) )
             r3 <- zipped.aread
             _ = assert( r3 == ("123",3) )
             r4 <- zipped.aread
             _ = assert( r4 == ("1234",4) )
             r5 <- zipped.aread
             l = assert( r5 == ("12345",5) )
        } yield l
    }

    test("zip operation from two finite channels") {
        val ch1 = Input.asInput(List(1,2),gopherApi)
        val ch2 = Input.asInput(List(1,2,3,4,5,6),gopherApi)
        val zipped = ch1 zip ch2
        for{
            r1 <- zipped.aread
            a1 = assert(r1 == (1, 1))
            r2 <- zipped.aread
            a2 = assert(r2 == (2,2))
            r3 <- recoverToSucceededIf[ChannelClosedException]{ zipped.aread }
        } yield r3
    }

    test("take from zip") {
        val ch1 = Input.asInput(List(1,2,3,4,5),gopherApi)
        val ch2 = Input.asInput(List(1,2,3,4,5,6),gopherApi)
        val zipped = ch1 zip ch2
        for {ar <- zipped.atake(5)
             _ <- assert(ar(0) == (1, 1))
             l <- assert(ar(4) == (5, 5))
        } yield l
    }

    test("taking from iterator-input") {
        val ch1 = Input.asInput(List(1,2,3,4,5),gopherApi)
        for( ar <- ch1.atake(5) ) yield assert(ar(4)==5)
    }

    test("zip with self will no dup channels, but generate (odd, even) pairs. It's a feature, not a bug") {
        val ch = gopherApi.makeChannel[Int]()
        val zipped = ch zip ch
        ch.awriteAll(List(1,2,3,4,5,6,7,8))
        for{ r1 <- zipped.aread
             a1 = assert( Set((1,2),(2,1)) contains r1  )
             r2 <- zipped.aread
             a2 = assert( Set((3,4),(4,3)) contains r2  )
             r3 <- zipped.aread
             a3 = assert( Set((5,6),(6,5)) contains r3  )
        } yield a3
    }

    test("reading from Q1|Q2") {

        val ch1 = gopherApi.makeChannel[Int]()
        val ch2 = gopherApi.makeChannel[Int]()

        val ar1 = (ch1 | ch2).aread
        ch1.awrite(1)
        for{
            r1 <- ar1
            a1 <- assert( r1==1 )
            ar2 = (ch1 | ch2).aread
            _ = ch2.awrite(2)
            r2 <- ar2
            a2 <- assert( r2==2 )
        } yield a1

    }

    test("simultanuos reading from Q1|Q2") {

        val ch1 = gopherApi.makeChannel[Int]()
        val ch2 = gopherApi.makeChannel[Int]()

        val ar1 = (ch1 | ch2).aread
        val ar2 = (ch1 | ch2).aread

        ch1.awrite(1)
        ch2.awrite(2)

        for {r1 <- ar1
             r2 <- ar2
             _ = if (r1 == 1) {
                 assert(r2 == 2)
             } else {
                 assert(r2 == 1)
             }
             r3 <- recoverToSucceededIf[TimeoutException] {
                timeouted( (ch1 | ch2).aread, 300 milliseconds)
             }
        } yield r3

    }


    test("reflexive or  Q|Q") {
        val ch = gopherApi.makeChannel[Int]()
        val aw1 = ch.awrite(1)
        val ar1 = (ch | ch).aread
        for {r1 <- ar1
             _ = assert(r1 == 1)
             ar2 = (ch | ch).aread
             r2_1 <- recoverToSucceededIf[TimeoutException] {
                 timeouted(ar2, 300 milliseconds)
             }
             _ = ch.awrite(3)
             r2 <- ar2
             a = assert(r2 == 3)
        } yield a
    }

    test("two items read from Q1|Q2") {
        val ch1 = gopherApi.makeChannel[Int]()
        val ch2 = gopherApi.makeChannel[Int]()
        val aw1 = ch1.awrite(1)
        val aw2 = ch2.awrite(2)
        val chOr = (ch1 | ch2)
        val ar1 = chOr.aread
        val ar2 = chOr.aread
        for {r1 <- ar1
             r2 <- ar2
        } yield assert( ((r1,r2)==(1,2)) ||((r1,r2)==(2,1)) )
    }

    test("atake read from Q1|Q2") {
        val ch1 = gopherApi.makeChannel[Int]()
        val ch2 = gopherApi.makeChannel[Int]()

        val aw1 = ch1.awriteAll(1 to 2)
        val aw2 = ch2.awriteAll(1 to 2)
        val at = (ch1 | ch2).atake(4)
        for( r <- at) yield assert(r.nonEmpty)
    }

    test("awrite/take ") {
        val ch = gopherApi.makeChannel[Int]()
        val aw = ch.awriteAll(1 to 100)
        val at = ch.atake(100)
        for (r <- at) yield assert(r.size == 100)
    }



    def gopherApi = CommonTestObjects.gopherApi

    def timeouted[T](f:Future[T],timeout:FiniteDuration):Future[T] =
    {
        val p = Promise[T]()
        p.completeWith(f)
        gopherApi.actorSystem.scheduler.scheduleOnce(timeout){
            p.failure(new TimeoutException)
        }
        p.future
    }

}


class InputOpsSyncSuite extends FunSuite with Waiters {

  import scala.concurrent.ExecutionContext.Implicits.global

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
