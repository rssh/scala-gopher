package hofasync

import cps._
import cps.monads.FutureAsyncMonad
import gopher._
import scala.concurrent.Future



class TestSharedMin extends munit.FunSuite {

      test("snhared-init") {
         import scala.concurrent.ExecutionContext.Implicits.global
         val gopherApi = SharedGopherAPI.apply[Future]()
         val ch = gopherApi.makeChannel[Int](1)
         val fw1 = ch.awrite(2)
         val fr1 = ch.aread()
         //implicit val printCode = cps.macroFlags.PrintCode
         async[Future] {
            val r1 = await(fr1)
            assert( r1 == 2 )
         }  
      }

}


