package gopher.channels

import gopher._
import cps._
import munit._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Channel=>_,_}
import scala.concurrent.duration._

import cps.monads.FutureAsyncMonad

class ReadChannelFactoryTest extends FunSuite {

    given Gopher[Future] = Gopher[Future]()


    test("unfoldAsync produce stream simple") {
      val  ch = ReadChannel.unfoldAsync(0){
        (x: Int) =>
          if (x > 10)  then
            Future successful None
          else
            Future successful Some(x,x+1)
      }

      ch.atake(20).map{ values =>
         assert(values(0) == 0)
         assert(values(1) == 1)
         assert(values(2) == 2)
         assert(values.size == 11)
      }

    }


    test("unfoldAsync prodce stream with error") {
      val  ch = ReadChannel.unfoldAsync(0){
        (x: Int) =>
          if (x > 3)  then
            Future failed new RuntimeException("state is too big")
          else
            Future successful Some(x,x+1)
      }

      async {
        val r0 = ch.read()
        assert(r0 == 0)
        val r1 = ch.read()
        assert(r1 == 1)
        val r2 = ch.read()
        assert(r2 == 2)
        val r3 = ch.read()
        assert(r3 == 3)
        var wasTooBig = false
        try {
          val r4 = ch.read()
        }catch{
           case e: RuntimeException =>
            wasTooBig = true
        }
        assert(wasTooBig)
      }


    }

}
