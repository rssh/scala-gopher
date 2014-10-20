package gopher.channels

import gopher._
import gopher.channels._
import gopher.tags._
import scala.concurrent._
import scala.concurrent.duration._
import scala.util._

import org.scalatest._
import org.scalatest.concurrent._

import gopher._
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class IOTimeoutsSuite extends FunSuite with AsyncAssertions {

  
  test("messsaged from timeouts must be appear during reading attempt from empty channel") {
      val ch = gopherApi.makeChannel[String]()
      val (chReady, chTimeout) = ch.trackInputTimeouts(300 milliseconds)
      val w = new Waiter()
      val f = gopherApi.select.once {
                case x: chReady.read => 1
                case x: chTimeout.read => 2
      } 
      Await.ready(f map ( x => { w( x == 2); w.dismiss() } ), 1 second )
      w.await()
  }

  test("when we have value, we have no timeouts") {
      val ch = gopherApi.makeChannel[String]()
      ch.awrite("qqq")
      val (chReady, chTimeout) = ch.trackInputTimeouts(300 milliseconds)
      val w = new Waiter()
      val f = gopherApi.select.once {
                case x: chReady.read => 1
                case x: chTimeout.read => 2
      } 
      Await.ready(f map ( x => { w( x == 1); w.dismiss() } ), 1 second )
      w.await()
  }

  
  def gopherApi = CommonTestObjects.gopherApi

  
}

