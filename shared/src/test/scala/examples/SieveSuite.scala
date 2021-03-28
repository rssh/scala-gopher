package example

import gopher._
import cps._
import munit._

import scala.concurrent.{Channel => _, _}
import scala.language.postfixOps

import cps.monads.FutureAsyncMonad 
import scala.concurrent.ExecutionContext.Implicits.global


/**
 * this is direct translation from appropriative go example.
 **/
object Sieve
{


  given gopherApi: Gopher[Future] = SharedGopherAPI.apply[Future]()


  def generate(n:Int, quit:Promise[Boolean]):Channel[Future,Int,Int] =
  {
    val channel = makeChannel[Int]()
    channel.awriteAll(2 to n) foreach (_ => quit success true)
    channel
  }

  // direct translation from go

  def filter0(in:Channel[Future,Int,Int]):ReadChannel[Future,Int] =
  {
    val filtered = makeChannel[Int]()
    var proxy: ReadChannel[Future, Int] = in;
    async {
      // since proxy is var, we can't select from one in forever loop.
      while(true) {
          val prime = proxy.read()
          proxy = proxy.filter(_ % prime != 0)
          filtered.write(prime)
      } 
    }
    filtered
  }


  def filter1(in:Channel[Future,Int,Int]):ReadChannel[Future,Int] =
  {
   val q = makeChannel[Int]()
   val filtered = makeChannel[Int]()
   select.afold(in){ ch => 
     select{
       case prime: ch.read => 
                         filtered.write(prime)
                         ch.filter(_ % prime != 0)
     }
   }
   filtered
  }

  def primes(n:Int, quit: Promise[Boolean]):ReadChannel[Future,Int] =
    filter1(generate(n,quit))

}

class SieveSuite extends FunSuite
{

 import Sieve.gopherApi


 test("last prime before 1000") {

   val quit = Promise[Boolean]()
   val quitInput = quit.future.asChannel

   val pin = Sieve.primes(1000,quit)

   var lastPrime=0;
   async {
    select.loop{
       case p: pin.read => 
                   if (false) {
                     System.err.print(p)
                     System.err.print(" ")
                   }
                   lastPrime=p
                   true
       case q: quitInput.read =>
                   //System.err.println()
                   false
    }
    assert( lastPrime == 997 )
  }
 }



}

