package gopher.channels

import gopher._
import gopher.channels._
import gopher.tags._

import org.scalatest._

import scala.language._
import scala.concurrent._
import scala.concurrent.duration._

class SelectErrorSuite extends FunSuite
{

   import scala.concurrent.ExecutionContext.Implicits.global

  

   test("select error instead foreach")  {
     import gopherApi._
     val channel = makeChannel[Int](100)

     var svEx: Throwable = null

     val g = go {
       var nWrites = 0
       var nErrors = 0
       for (s <- select.forever) {
         s match {
           case x: channel.write if (x == nWrites) =>
             nWrites = nWrites + 1
             if (nWrites == 50) {
               throw new RuntimeException("Be-be-be")
             }
             if (nWrites == 100) {
               select.exit(())
             }
           case ex: select.error =>
             { };  svEx = ex  // macro-system errors: assignments accepts as default argument
         }
       }
     }

     val tf = channel.atake(60)

     Await.ready(tf, 10 seconds)

     assert(svEx.getMessage == "Be-be-be")



   }



   lazy val gopherApi = CommonTestObjects.gopherApi
   
}
