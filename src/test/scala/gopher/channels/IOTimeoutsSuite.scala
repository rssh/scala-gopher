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
      Await.ready(f map ( x => { w( assert(x == 2) ); w.dismiss() } ), 1 second )
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
      Await.ready(f map ( x => { w( assert(x == 1) ); w.dismiss() } ), 1 second )
      w.await()
  }


  test("on channel close timeout channel also must close", Now) {
      val w = new Waiter()
      val ch = gopherApi.makeChannel[String]()
      Await.ready(ch.awrite("qqq"), 1 second)
      val (chReady, chTimeout) = ch.trackInputTimeouts(300 milliseconds)
      ch.close()
      // now must read one element
      val f1 = gopherApi.select.once {
                case x: chReady.read => 1
                case x: chTimeout.read => 2
              }
      Await.ready(f1 map ( x => { w( x == 1); w.dismiss()  } ), 1 second )
      // now receive stream-closed exception
      val f2 = chReady.aread
      f2 onComplete { x => w(assert(x.isFailure && x.failed.get.isInstanceOf[ChannelClosedException]))
                           w.dismiss()
      } 
      Await.ready(f2, 1 second)
      val f3 = chTimeout.aread
      f3 onComplete { x => w(assert(x.isFailure && x.failed.get.isInstanceOf[ChannelClosedException]))
                           w.dismiss()
      } 
      Await.ready(f3, 1 second)
      w.await(dismissals=Dismissals(3))
               
  }

  
  def gopherApi = CommonTestObjects.gopherApi

  
}

