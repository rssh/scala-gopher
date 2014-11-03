package gopher.transputers

import gopher._
import org.scalatest._
import scala.concurrent._
import scala.concurrent.duration._



class TransputerRestartSuite extends FunSuite
{

  test("bingo copy call") {
    // val bingo = gopherApi.makeTransputer[BingoWithRecover]
    val bingo = { def factory(): BingoWithRecover = new BingoWithRecover {
                        def api = gopherApi
                        def recoverFactory = factory
                     }
      val retval = factory()
      retval
     }

     val bingo1 = bingo.recoverFactory()
     bingo1.copyPorts(bingo)
  }


  def gopherApi = new GopherAPI()

}

