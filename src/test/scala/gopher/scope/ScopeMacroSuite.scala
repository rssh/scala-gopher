package anypackage.sub



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
    val s = goScope{ defer{ recover("CCC") } ; panic("AAA"); "4" }
    assert(s=="CCC")
  }
  

}