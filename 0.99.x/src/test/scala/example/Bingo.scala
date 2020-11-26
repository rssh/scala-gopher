package examples

import akka.actor._
import gopher._
import gopher.channels._
import org.scalatest._

import scala.language._


trait Bingo extends SelectTransputer
{

  val inX = InPort[Int]()
  val inY = InPort[Int]()
  val out = OutPort[Boolean]()

  loop {
       case x: inX.read =>
               val y = inY.read
               //Console.println(s"Bingo checker, received ${x}, ${y}")
               out.write(x==y)
  }

  recover {
    case ex: ChannelClosedException => SupervisorStrategy.Stop
  }

}

trait Acceptor extends SelectTransputer
{

  val inA = InPort[Boolean]()

  var nBingos = 0
  var nPairs = 0

  loop {
          case x: inA.read =>
             // Console.println(s"acceptor: ${nPairs} ${nBingos} ${x}")
              if (x) {
                 nBingos += 1
              }
              nPairs += 1
  }

}

class BingoSuite extends AsyncFunSuite
{

  test("bingo process wit identical input must return same") {
     val inX = gopherApi.iterableInput(1 to 100)
     val inY = gopherApi.iterableInput(1 to 100)
     val bingo = gopherApi.makeTransputer[Bingo]
     val acceptor = gopherApi.makeTransputer[Acceptor]
     bingo.inX connect inX
     bingo.inY connect inY
     bingo.out >~~> acceptor.inA
     val w = (bingo + acceptor).start()
     w map { u =>
       assert(acceptor.nBingos == acceptor.nPairs)
     }
  }

  def gopherApi = CommonTestObjects.gopherApi

}

