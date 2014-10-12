package gopher.channels

import gopher._
import gopher.channels._
import gopher.tags._
import scala.concurrent._
import scala.concurrent.duration._

import org.scalatest._

import scala.concurrent.ExecutionContext.Implicits.global

class InputOpsSuite extends FunSuite {

  
  test("map operation for input") {
      val ch = gopherApi.makeChannel[String]()
      ch.awriteAll(List("AAA","123","1234","12345"))
      val mappedCh = ch map (_.reverse)
      val r = ch.atake(4) map { l =>
                assert(l(0) == "54321")
                assert(l(1) == "4321")
                assert(l(2) == "321")
                assert(l(3) == "AAA")
              }
      Await.ready(r, 10 seconds) 
  }
  
  test("filter operation for input") {
      val ch = gopherApi.makeChannel[String]()
      ch.awriteAll(List("qqq", "AAA","123","1234","12345"))
      val filteredCh = ch filter (_.contains("A"))
      val readed = Await.result( filteredCh.aread, 10 seconds )
      assert(readed == "AAA")
  }


  test("zip operation for two simple inputs") {
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


  def gopherApi = CommonTestObjects.gopherApi

  
}
