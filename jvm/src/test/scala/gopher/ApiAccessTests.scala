package gopher


import scala.concurrent._
import scala.concurrent.duration._
import cps._
import cps.monads.FutureAsyncMonad
import scala.language.postfixOps



class ApiAccessTests extends munit.FunSuite {


    import scala.concurrent.ExecutionContext.Implicits.global

    test("simple channel") {
        given Gopher[Future] = JVMGopher[Future]()
        val ch = makeChannel[Int](1)
        val fw1 = ch.awrite(1)
        val fr1 = ch.aread
        val r1 = Await.result(fr1, 1 second)
        assert( r1 == 1 )
    }

}