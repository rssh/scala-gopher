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


  def gopherApi = CommonTestObjects.gopherApi

  
}
