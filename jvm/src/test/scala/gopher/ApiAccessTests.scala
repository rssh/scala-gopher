package gopher


import scala.concurrent._
import scala.concurrent.duration._
import cps._
import cps.monads.FutureAsyncMonad
import scala.language.postfixOps

import munit._

class ApiAccessTests extends FunSuite {


    import scala.concurrent.ExecutionContext.Implicits.global

    test("simple unbuffered channel") {
        given Gopher[Future] = JVMGopher[Future]()
        val ch = makeChannel[Int](0)
        val fw1 = ch.awrite(1)
        //println("after awrite")
        val fr1 = ch.aread
        //println("after aread, waiting result")
        val r1 = Await.result(fr1, 1 second)
        assert( r1 == 1 )
    }

    test("simple 1-buffered channel") {
        given Gopher[Future] = JVMGopher[Future]()
        val ch = makeChannel[Int](1)
        val fw1 = ch.awrite(1)
        val fr1 = ch.aread
        val r1 = Await.result(fr1, 1 second)
        assert( r1 == 1 )
    }

}