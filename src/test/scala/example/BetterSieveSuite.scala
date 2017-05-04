package example

import gopher.channels.CommonTestObjects.gopherApi._
import gopher.channels._
import org.scalatest._

import scala.concurrent._
import scala.language.postfixOps

/**
 * more 'scala-like' sieve
 **/
object BetterSieve
{
  import scala.concurrent.ExecutionContext.Implicits.global


  def generate(n:Int, quit:Promise[Boolean]):Input[Int] =
  {
    val channel = makeChannel[Int]()
    channel.awriteAll(2 to n) foreach (_ => quit success true)
    channel
  }

  /**
   * flatFold modify channel with each read
   */
  def filter(in:Input[Int]):Input[Int] = 
    in.flatFold{ (s,prime) => s.filter( _ % prime != 0) }

  def primes(n:Int, quit: Promise[Boolean]):Input[Int] =
    filter(generate(n,quit))

}

class BetterSieveSuite extends AsyncFunSuite
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
   future map { u =>
     assert(lastPrime == 997)
   }
 }

}

