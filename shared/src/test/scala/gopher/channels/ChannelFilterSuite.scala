package gopher.channels

import gopher._
import scala.concurrent.Future
import scala.util.Try
import scala.util.Success
import scala.util.Failure

import cps._
import cps.monads.FutureAsyncMonad

import munit._


class ChannelFilterSuite extends FunSuite:

  import scala.concurrent.ExecutionContext.Implicits.global
  given Gopher[Future] = SharedGopherAPI.apply[Future]()


  test("odd filter should leave only odd numbers in filtered channel") {

     val ch = makeChannel[Int]()

     val filtered = ch.filter(_ % 2 == 0)

     ch.awriteAll(1 to 100)
     async {
        var i = 0
        while(i < 50) {
          val x = filtered.read
          assert( x % 2 == 0)
          i=i+1
        }
     }

  }