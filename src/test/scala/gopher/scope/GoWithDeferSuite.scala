package gopher.scope


import org.scalatest.FunSuite
import gopher._
import gopher.tags._

import scala.language._
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class GoWithDeferSuite extends FunSuite {
  

  test("2.1. goWithDefer: simple statement with defer must be processed") {
    @volatile var x = 0
    val f = go {
      defer{ x = 2 }
      x = 1
    }
    Await.ready(f, 1 second)
    assert(x === 2)
  }

  test("2.2. typed go with recover") {
    var x = 0
    val s = go{ defer{ recover{case ex: Throwable => "CCC"} } ; throw new RuntimeException("AA-go"); "4" }
    Await.ready(s, 1 second)
    assert(Await.result(s, 1 second)=="CCC")
  }

  // TODO: go with select.

}


