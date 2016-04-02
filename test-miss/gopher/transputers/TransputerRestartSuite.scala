package gopher.transputers

import scala.language._
import gopher._
import gopher.channels._
import gopher.tags._
import org.scalatest._
import scala.concurrent._
import scala.concurrent.duration._
import akka.actor._

class MyException extends RuntimeException("AAA")

trait BingoWithRecover extends SelectTransputer with TransputerLogging
{

  val inX = InPort[Int]()
  val inY = InPort[Int]()
  val out = OutPort[Boolean]()
  val fin = OutPort[Boolean]()

  var exReaction: SupervisorStrategy.Directive = SupervisorStrategy.Restart
  var throwAlways: Boolean = false

  override def copyState(prev: Transputer):Unit =
  {
    val bingoPrev = prev.asInstanceOf[BingoWithRecover]
    exReaction = bingoPrev.exReaction
    throwAlways = bingoPrev.throwAlways
  }


  recover {
       case ex: ChannelClosedException => 
                                     SupervisorStrategy.Stop
       case ex: MyException => 
                                     SupervisorStrategy.Restart
  }

  loop {
       case x: inX.read =>
               val y = inY.read
               //log.info(s"Bingo checker, received ${x}, ${y} ")
               out.write(x==y)
               if (x==2) {
                 throw new MyException()
               } else if (x > 2 && throwAlways) {
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

  test("bingo restore with the same connectons") {
     val inX = gopherApi.iterableInput(1 to 100)
     val inY = gopherApi.iterableInput(1 to 100)
     val bingo = gopherApi.makeTransputer[BingoWithRecover]
     bingo.exReaction = SupervisorStrategy.Restart
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

  test("bingo resume") {
     val inX = gopherApi.iterableInput(1 to 100)
     val inY = gopherApi.iterableInput(1 to 100)
     val bingo = gopherApi.makeTransputer[BingoWithRecover]
     bingo.exReaction = SupervisorStrategy.Resume
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

  test("bingo - too many failures with restart") {
     val inX = gopherApi.iterableInput(1 to 100)
     val inY = gopherApi.iterableInput(1 to 100)
     val bingo = gopherApi.makeTransputer[BingoWithRecover]
     bingo.exReaction = SupervisorStrategy.Restart
     bingo.throwAlways = true
     val acceptor = gopherApi.makeTransputer[Acceptor1]
     val fin = gopherApi.makeChannel[Boolean]()
     bingo.inX connect inX
     bingo.inY connect inY
     bingo.out >~~> acceptor.inA
     bingo.fin connect fin
     val w = (bingo + acceptor).start()
     intercept[Transputer.TooManyFailures] {
        Await.result(w,10 seconds) 
     }
  }

  test("bingo - too many failures with resume") {
     val inX = gopherApi.iterableInput(1 to 100)
     val inY = gopherApi.iterableInput(1 to 100)
     val bingo = gopherApi.makeTransputer[BingoWithRecover]
     bingo.exReaction = SupervisorStrategy.Resume
     bingo.throwAlways = true
     val acceptor = gopherApi.makeTransputer[Acceptor1]
     val fin = gopherApi.makeChannel[Boolean]()
     bingo.inX connect inX
     bingo.inY connect inY
     bingo.out >~~> acceptor.inA
     bingo.fin connect fin
     val w = (bingo + acceptor).start()
     intercept[Transputer.TooManyFailures] {
        Await.result(w,10 seconds) 
     }
  }


  def gopherApi = CommonTestObjects.gopherApi

}

