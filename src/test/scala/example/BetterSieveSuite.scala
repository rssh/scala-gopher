package example

import gopher._
import gopher.channels._
import CommonTestObjects.gopherApi._
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.language.postfixOps

import org.scalatest._

/**
 * more 'scala-like' sieve
 **/
object BetterSieve
{

  def generate(n:Int, quit:Promise[Boolean]):IOChannel[Int] =
  {
    val channel = makeChannel[Int]()
    channel.awriteAll(2 to n) foreach (_ => quit success true)
    channel
  }

  /**
   * flatFold modify channel with each read
   */
  def filter(in:IOChannel[Int]):Input[Int] = 
    in.flatFold{ (s,prime) => s.filter( _ % prime != 0) }

  def primes(n:Int, quit: Promise[Boolean]):Input[Int] =
    filter(generate(n,quit))

}

class BetterSieveSuite extends FunSuite
{

 test("last prime before 1000") {

   val quit = Promise[Boolean]()
   val quitInput = futureInput(quit.future)

   val pin = Sieve.primes(1000,quit)

   var lastPrime=0;
   val future = select.forever {
       case p: pin.read => 
                   if (false) {
                     System.err.print(p)
                     System.err.print(" ")
                   }
                   lastPrime=p
       case q: quitInput.read =>
                   //System.err.println()
                   CurrentFlowTermination.exit(());
   }
   Await.ready(future, 10 seconds)
   assert( lastPrime == 997)
 }

}

