package gopher.scope

import org.scalatest.FunSuite


class SimpleStatementSuite extends FunSuite
{

  test("goScoped: simple statement without defer must be processed") {
    var x = 0
    implicit val sc = new ScopeContext
    goScope {
      x = 1
    }
    assert(x === 1)
  }

  test("goScoped: simple statement with defer must be processed") {
    var x = 0
    implicit val sc = new ScopeContext
    goScoped {
      _defer{ x = 2 }
      x = 1
    }
    assert(x === 2)
  }

  test("goScoped: simple statement with panic must be processed") {
    implicit val sc = new ScopeContext
    var x = 0
    goScoped {
      _defer{ 
        System.out.println("recover");
        x=1 
        _recover(())
      } 
      if (x==0) {
         System.out.println("panic");
         _panic("x==0");
      }
    }
    assert(x==1)
  }

}

