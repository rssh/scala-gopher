package gopher.scope

import org.scalatest.FunSuite


class SimpleStatementSuite extends FunSuite
{

  test("goScoped: simple statement without defer must be processed") {
    var x = 0
    implicit val sc = new ScopeContext[Unit]
    goScope {
      x = 1
    }
    assert(x === 1)
  }

  test("goScoped: simple statement with defer must be processed") {
    var x = 0
    implicit val sc = new ScopeContext[Unit]
    goScoped {
      defer{ x = 2 }
      x = 1
    }
    assert(x === 2)
  }

  test("goScoped: simple statement with panic must be processed") {
    implicit val sc = new ScopeContext[Unit]
    var x = 0
    goScoped {
      defer{ 
        System.out.println("recover");
        x=1 
        recover(())
      } 
      if (x==0) {
         System.out.println("panic");
         panic("x==0");
      }
    }
    assert(x==1)
  }

}

