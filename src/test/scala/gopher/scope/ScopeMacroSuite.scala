package gopher.scope


import org.scalatest.FunSuite
import gopher._



class ScopeMacroSuite extends FunSuite {
  

  test("goScope: simple statement with defer must be processed") {
    var x = 0
    goScope {
      defer{ x = 2 }
      x = 1
    }
    assert(x === 2)
  }

  test("typed goScope") {
    var x = 0
    val s = goScope{ defer{ recover{case ex: Throwable => "CCC"} } ; throw new RuntimeException("AA"); "4" }
    assert(s=="CCC")
  }
  

}


