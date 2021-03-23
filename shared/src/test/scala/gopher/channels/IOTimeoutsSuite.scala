package gopher.channels

import gopher._
import cps._
import gopher._
import munit._

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps

import cps.monads.FutureAsyncMonad


class IOTimeoutsSuite extends FunSuite {

    import scala.concurrent.ExecutionContext.Implicits.global
    given Gopher[Future] = SharedGopherAPI.apply[Future]() 

    test("messsaged from timeouts must be appear during reading attempt from empty channel") {
        val ch = makeChannel[String]()
        //val (chReady, chTimeout) = ch.withInputTimeouts(300 milliseconds)
        async {
          val f = select.once {
            case x: ch.read => 1
            case t: Time.after if t == (300 milliseconds) => 2
          }
          assert(f==2)
        }
    }

    

    test("when we have value, we have no timeouts") {
        val ch = makeChannel[String]()
        ch.awrite("qqq")
        //val (chReady, chTimeout) = ch.withInputTimeouts(300 milliseconds)
        async {
            val x = select.once {
                case x: ch.read => 1
                case t: Time.after if t == (300 milliseconds) => 2
            }
            assert (x==1)
        }
    }

    
    test("messsaged from timeouts must be appear during attempt to write to filled unbuffered channel") {
        val ch = makeChannel[Int]()
        //val (chReady, chTimeout) = ch.withOutputTimeouts(150 milliseconds)
        async {
            @volatile var count = 1
            select.loop{
                case x: ch.write if (x==count) =>
                    count += 1  // will newer called, since we have no reader
                    true
                case t: Time.after if t == (150 milliseconds) =>
                    false
            }
            assert(count==1)
        }
    }

    
    test("messsaged from timeouts must be appear during attempt to write to filled buffered channel") {
        val ch = makeChannel[Int](1)
        //val (chReady, chTimeout) = ch.withOutputTimeouts(150 milliseconds)
        async{
            @volatile var count = 1
            select.loop {
                case x: ch.write if (x==count) =>
                   count += 1
                   true
                case t: Time.after if t == (150 milliseconds) =>
                   false
            }
            assert(count==2)
        }
    }


    test("when we have where to write -- no timeouts") {
        val ch =  makeChannel[Int](1)
        //val (chReady, chTimeout) = ch.withOutputTimeouts(300 milliseconds)
        async {
            val x = select.once {
                case x: ch.write if (x==1) => 1
                case t: Time.after if t == (150 milliseconds) => 2
            }
            assert(x == 1) 
        }
    }

    
    
    test("during 'normal' processing timeouts are absent") {

        //implicit val printCode = cps.macroFlags.PrintCode
  
        val ch = makeChannel[Int]()
        //val (chInputReady, chInputTimeout) = ch.withInputTimeouts(300 milliseconds)
        //val (chOutputReady, chOutputTimeout) = ch.withOutputTimeouts(300 milliseconds)
        @volatile var count = 0
        @volatile var count1 = 0
        @volatile var wasInputTimeout = false
        @volatile var wasOutputTimeout = false
        val maxCount = 100

        val fOut = async {
            select.loop {
                case x: ch.write if (x==count) =>
                    if (count == maxCount) {
                        false
                    } else {
                        count += 1
                        true
                    }
            case t: Time.after if t == (300 milliseconds) =>
                    wasOutputTimeout = true
                    true
            }
        }
        val fIn = async {
            select.loop {
                case x: ch.read =>
                    count1 = x
                    (x != maxCount) 
                case t: Time.after if t == (150 milliseconds) =>
                    wasInputTimeout = true
                    true
            }
        }
        for{
           _ <- fOut
           _ <- fIn
           _ = assert(count == maxCount)
           _ = assert(count1 == maxCount)
        } yield assert(!wasOutputTimeout && !wasInputTimeout)
    }


}

