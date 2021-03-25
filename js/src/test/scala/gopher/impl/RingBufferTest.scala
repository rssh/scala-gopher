package gopher.impl

import cps._
import cps.monads.FutureAsyncMonad
import gopher._
import scala.concurrent._
import scalajs.concurrent.JSExecutionContext
import scalajs.concurrent.JSExecutionContext.Implicits.queue


import munit._

class RingBufferTests extends munit.FunSuite{

   test("ring buffer ") {

        val gopher = JSGopher[Future](JSGopherConfig())
        val ch = gopher.makeChannel[Int](3)

        var x = 0

        async[Future] {
           ch.write(1)
           ch.write(2)
           ch.write(3)
           // we should be blocked before sending next
           JSExecutionContext.queue.execute{ () =>
              x = 1
              ch.aread()
           }
           ch.write(4)
           assert(x != 0)
        }

   }


}