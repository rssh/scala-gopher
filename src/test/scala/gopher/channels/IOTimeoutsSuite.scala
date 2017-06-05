package gopher.channels

import gopher._
import org.scalatest._

import scala.concurrent.duration._
import scala.language._

class IOTimeoutsSuite extends AsyncFunSuite {

    test("messsaged from timeouts must be appear during reading attempt from empty channel") {
        val ch = gopherApi.makeChannel[String]()
        val (chReady, chTimeout) = ch.withInputTimeouts(300 milliseconds)
        val f = gopherApi.select.once {
            case x: chReady.read => 1
            case x: chTimeout.read => 2
        }
        for(x <- f) yield assert( x == 2)
    }


    test("when we have value, we have no timeouts") {
        val ch = gopherApi.makeChannel[String]()
        ch.awrite("qqq")
        val (chReady, chTimeout) = ch.withInputTimeouts(300 milliseconds)
        val f = gopherApi.select.once {
            case x: chReady.read => 1
            case x: chTimeout.read => 2
        }
        for(x <- f) yield assert (x==1)
    }


    test("on input close it's timeout channel also must close") {
        // want to use 'read || ec instead serialized
        implicit val executionContext = scala.concurrent.ExecutionContext.Implicits.global
        val ch = gopherApi.makeChannel[String](1)
        for{
            _ <- ch.awrite("qqq")
            (chReady, chTimeout) = ch.withInputTimeouts(300 milliseconds)
            _ = ch.close()
            f1 = gopherApi.select.once {
                case x: chReady.read => 1
                case x: chTimeout.read => 2
            }
            x <- f1
            _ <- assert(x==1)
            _ <- recoverToSucceededIf[ChannelClosedException] {
                chReady.aread
            }
            l <- recoverToSucceededIf[ChannelClosedException] {
                chTimeout.aread
            }
        } yield l
    }

    test("messsaged from timeouts must be appear during attempt to write to filled unbuffered channel") {
        val ch = gopherApi.makeChannel[Int]()
        val (chReady, chTimeout) = ch.withOutputTimeouts(150 milliseconds)
        @volatile var count = 1
        val f = gopherApi.select.forever {
            case x: chReady.write if (x==count) =>
            {};
                count += 1  // will newer called, since we have no reader
            case t: chTimeout.read =>
                implicitly[FlowTermination[Unit]].doExit(count)
        }
        f map (_ => assert(count==1))
    }

    test("messsaged from timeouts must be appear during attempt to write to filled buffered channel") {
        val ch = gopherApi.makeChannel[Int](1)
        val (chReady, chTimeout) = ch.withOutputTimeouts(150 milliseconds)
        @volatile var count = 1
        val f = gopherApi.select.forever {
            case x: chReady.write if (x==count) =>
            {};
                count += 1
            case t: chTimeout.read =>
                implicitly[FlowTermination[Unit]].doExit(count)
        }
        f map { _ => assert(count==2) }
    }

    test("when we have where to write -- no timeouts") {
        val ch = gopherApi.makeChannel[Int](1)
        val (chReady, chTimeout) = ch.withOutputTimeouts(300 milliseconds)
        val f = gopherApi.select.once {
            case x: chReady.write if (x==1) => 1
            case t: chTimeout.read => 2
        }
        f map { r => assert(r == 1) }
    }

    test("on output close it's timeout channel also must close") {
        val ch = gopherApi.makeChannel[Int](1)
        val (chReady, chTimeout) = ch.withOutputTimeouts(300 milliseconds)
        val f1 = chReady.awrite(1)
        for {
            x1 <- f1
            _ <- assert(x1 == 1)
            _ = ch.close()
            l <- recoverToSucceededIf[ChannelClosedException] {
                chReady.awrite(2)
            }
        } yield l
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
        for{
           _ <- fOut
           _ <- fIn
           _ = assert(count == maxCount)
           _ = assert(count1 == maxCount)
        } yield assert(!wasOutputTimeout && !wasInputTimeout)
    }




    def gopherApi = CommonTestObjects.gopherApi


}

