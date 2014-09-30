package gopher.channels

import gopher._
import gopher.channels._
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

  def gopherApi = CommonTestObjects.gopherApi

  
}
