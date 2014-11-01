package gopher.transputers

import gopher._
import gopher.channels._
import gopher.tags._
import org.scalatest._
import scala.concurrent._
import scala.concurrent.duration._
import akka.actor._

class MyException extends RuntimeException("AAA")

trait BingoWithRestart extends SelectTransputer
{

  val inX = InPort[Int]()
  val inY = InPort[Int]()
  val out = OutPort[Boolean]()
  val fin = OutPort[Boolean]()

  recover {
       case ex: ChannelClosedException => 
                                     SupervisorStrategy.Stop
       case ex: MyException => 
                                     SupervisorStrategy.Restart
  }

  loop {
       case x: inX.read =>
               val y = inY.read
               //Console.println(s"Bingo checker, received ${x}, ${y}")
               out.write(x==y)
               if (x==2) {
                 throw new MyException()
               }
               if (x==100) {
                 fin.write(true)
               }
  }

}

trait Acceptor1 extends SelectTransputer
{

  val inA = InPort[Boolean]()

  @volatile var nBingos = 0
  @volatile var nPairs = 0

  loop {
          case x: inA.read =>
             // Console.println(s"acceptor: ${nPairs} ${nBingos} ${x}")
              if (x) {
                 nBingos += 1
              }
              nPairs += 1
  }

}

class TransputerRestartSuite extends FunSuite
{

  test("bingo must be restored with the same connectons", Now) {
     val inX = gopherApi.iterableInput(1 to 100)
     val inY = gopherApi.iterableInput(1 to 100)
     val bingo = gopherApi.makeTransputer[BingoWithRestart]
     val acceptor = gopherApi.makeTransputer[Acceptor1]
     val fin = gopherApi.makeChannel[Boolean]()
     bingo.inX connect inX
     bingo.inY connect inY
     bingo.out >~~> acceptor.inA
     bingo.fin connect fin
     (bingo + acceptor).start()
     val w = fin.aread
     Await.ready(w,10 seconds) 
     assert(acceptor.nBingos == acceptor.nPairs)
  }

  def gopherApi = CommonTestObjects.gopherApi

}

