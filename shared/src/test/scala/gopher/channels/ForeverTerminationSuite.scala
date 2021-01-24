package gopher.channels


import cps._
import gopher._
import munit._

import scala.concurrent.{Channel=>_,_}
import scala.language.postfixOps

import cps.monads.FutureAsyncMonad


class ForeverSuite extends FunSuite
{

  import ExecutionContext.Implicits.global
  given Gopher[Future] = SharedGopherAPI.apply[Future]()


  test("forevr not propagate signals after exit") {
    val channel = makeChannel[Int](100)
    var sum = 0
    val f0 = select.aforever {
                case x: channel.read => sum += x
                             throw ChannelClosedException()
            }
    for {r2 <- channel.awrite(1)
         r3 <- channel.awrite(2)
         r0 <- f0 } yield assert(sum == 1)

  }
  


}


