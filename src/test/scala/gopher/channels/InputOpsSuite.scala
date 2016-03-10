package gopher.channels

import gopher._
import gopher.channels._
import scala.language._
import scala.concurrent._
import scala.concurrent.duration._
import scala.util._

import org.scalatest._
import org.scalatest.concurrent._

import scala.concurrent.ExecutionContext.Implicits.global

object TestGlobals
{
  lazy val gopherApi = new GopherAPI()
}

class InputOpsSuite extends FunSuite with AsyncAssertions {

  import TestGlobals._ 

  test("channel afold with async operation inside") {
      val ch1 = gopherApi.makeChannel[Int](10) 
      val ch2 = gopherApi.makeChannel[Int](10) 
      val fs = ch1.afold(0){ (s,n) =>
                    val n1 = ch2.read
                    //s+(n1+n2) -- stack overflow in 2.11.8 compiler. TODO: submit bug
                    s+(n+n1)
                  }
      scala.async.Async.async {
       ch1.writeAll(1 to 10)
       ch2.writeAll(1 to 10)
       ch1.close()
      }
      val r = Await.result(fs, 10 seconds)
      assert(r==110)
  }




  
}
