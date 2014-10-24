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
      val (chReady, chTimeout) = ch.withInputTimeouts(300 milliseconds)
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
      val (chReady, chTimeout) = ch.withInputTimeouts(300 milliseconds)
      val w = new Waiter()
      val f = gopherApi.select.once {
                case x: chReady.read => 1
                case x: chTimeout.read => 2
      } 
      Await.ready(f map ( x => { w( assert(x == 1) ); w.dismiss() } ), 1 second )
      w.await()
  }


  test("on input close it's timeout channel also must close") {
      val w = new Waiter()
      val ch = gopherApi.makeChannel[String]()
      Await.ready(ch.awrite("qqq"), 1 second)
      val (chReady, chTimeout) = ch.withInputTimeouts(300 milliseconds)
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


  test("messsaged from timeouts must be appear during attempt to write to filled channel") {
      val ch = gopherApi.makeChannel[Int]()
      val (chReady, chTimeout) = ch.withOutputTimeouts(300 milliseconds)
      @volatile var count = 1
      val f = gopherApi.select.forever {
                case x: chReady.write if (x==count) => 
                            {};
                            count += 1
                case t: chTimeout.read =>
                            implicitly[FlowTermination[Unit]].doExit(count)
              }
      Await.ready(f, 1 second)
      assert(count==2)
  }

  test("when we have where to write -- no timeouts") {
      val ch = gopherApi.makeChannel[Int]()
      val (chReady, chTimeout) = ch.withOutputTimeouts(300 milliseconds)
      val f = gopherApi.select.once {
                 case x: chReady.write if (x==1) => 1
                 case t: chTimeout.read => 2
              }
      val r = Await.result(f, 1 second)
      assert(r == 1)
  }
  
  test("on output close it's timeout channel also must close") {
      val ch = gopherApi.makeChannel[Int]()
      val (chReady, chTimeout) = ch.withOutputTimeouts(300 milliseconds)
      val w = new Waiter()
      val f1 = chReady.awrite(1)
      f1 onComplete { case Success(x) => w{assert(x==1) }; w.dismiss()  }
      Await.ready(f1, 1 second)
      ch.close()
      val f2 = chReady.awrite(2)
      f2 onComplete { x => w(assert(x.isFailure && x.failed.get.isInstanceOf[ChannelClosedException]))
                           w.dismiss()
                    }
      w.await(dismissals=Dismissals(2))
  }

  test("during 'normal' processing timeouts are absent") {
      val ch = gopherApi.makeChannel[Int]()
      val (chInputReady, chInputTimeout) = ch.withInputTimeouts(300 milliseconds)
      val (chOutputReady, chOutputTimeout) = ch.withOutputTimeouts(300 milliseconds)
      @volatile var count = 0
      @volatile var count1 = 0
      @volatile var wasInputTimeout = false
      @volatile var wasOutputTimeout = false
      val maxCount = 100
      val fOut = gopherApi.select.forever {
                   case x: chOutputReady.write if (x==count) =>
                             if (count == maxCount) {
                                implicitly[FlowTermination[Unit]].doExit(())
                             } else {
                                count += 1 
                             }
                   case t: chOutputTimeout.read =>
                             {};
                             wasOutputTimeout = true
                } 
      val fIn = gopherApi.select.forever {
                   case x: chInputReady.read =>
                             count1 = x
                             if (x == maxCount) {
                                implicitly[FlowTermination[Unit]].doExit(())
                             }
                   case t: chInputTimeout.read =>
                             {};
                             wasInputTimeout = true
                }
      Await.ready(fOut, 1 second)
      Await.ready(fIn, 1 second)
      assert(count == maxCount) 
      assert(count1 == maxCount) 
      assert(!wasInputTimeout) 
      assert(!wasOutputTimeout) 
  }

  def gopherApi = CommonTestObjects.gopherApi

  
}

