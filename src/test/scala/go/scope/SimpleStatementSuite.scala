package go.scope

import org.scalatest.FunSuite
import scala.util.continuations._


class SimpleStatementSuite extends FunSuite
{

  test("reset/shift: simple statement without defer must be processed") {
    var x = 0
    var upK: Option[Unit=>Unit] = None
    try {
     reset{ 
      if (x == 0) {
        shift{ (k:Unit=>Unit)=> {
          x = 1
          upK = Some(k)
          throw new RuntimeException("qqq");
        }    }
      } else {
        shift{ (k:Unit=>Unit)=>k() }
      } 
      Console.println("after if"); 
     }
    } catch {
      case ex: RuntimeException =>
        if (ex.getMessage == "qqq") {
           Console.println("catched exception");
        } else {
           throw new RuntimeException(ex)
        }
    } finally {
     if (upK != None) {
         upK.get.apply()
     }
    }
    assert(x === 1)
  }

  test("goScoped: simple statement without defer must be processed") {
    var x = 0
    implicit val sc = new ScopeContext[Unit]
    goScoped {
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

