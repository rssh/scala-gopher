package gopher.scope


import org.scalatest.FunSuite
import gopher._
import gopher.tags._



class ScopeMacroSuite extends FunSuite {
  

  test("1. goScope: simple statement with defer must be processed") {
    var x = 0
    goScope {
      defer{ x = 2 }
      x = 1
    }
    assert(x === 2)
  }

  test("2. typed goScope") {
    var x = 0
    val s = goScope{ defer{ recover{case ex: Throwable => "CCC"} } ; throw new RuntimeException("AA"); "4" }
    assert(s=="CCC")
  }

  test("3. defered code must be called when non-local return") {

     var deferCalled = false

     def testFun(): Int =
      goScope {
         defer{ 
           deferCalled=true 
         }
         if (true) {
           return 34;
         }
         42
      }
 
     val q = testFun()

     assert(q==34)
     assert(deferCalled)

  }
  
  test("4. non-local return must not be catched in recover") {

     var thCatched = false

     def testFun(): Int =
       goScope {
         defer{ recover{ case th: Throwable => { thCatched=true; 5 } } }
         if (true) {
           return 10;
         }
         11
       }

     val q = testFun()
     assert(q==10)
     assert(!thCatched)

  }

}


