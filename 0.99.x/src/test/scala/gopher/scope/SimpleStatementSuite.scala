package gopher.scope

import org.scalatest.FunSuite
import gopher._
import gopher.tags._


class SimpleStatementSuite extends FunSuite
{

  test("withDefer: simple statement without defer must be processed") {
    var x = 0
    withDefer[Unit] { d=>
      x = 1
    }
    assert(x === 1)
  }

  test("withDefers: simple statement with defer must be processed") {
    var x = 0
    withDefer[Unit] { d =>
      d.defer{ x = 2 }
      x = 1
    }
    assert(x === 2)
  }

  test("withDefers: simple statement with panic must be processed") {
    var x = 0
    withDefer[Unit] { d=>
      d.defer { 
        d.recover{
           case ex: Throwable =>
                      x=1 
        }
      } 
      if (x==0) {
         throw new IllegalStateException("x==0");
      }
    }
    assert(x==1)
  }

  test("withDefers: recover must resturn value") {
    val x = withDefer[Int] { d=>
      val retval = 3;
      d.defer { d.recover {
         case ex: IllegalStateException => 5
      }}
      throw new IllegalStateException("Be-Be-Be")
      retval
    }
    assert(x==5)
  }

}

