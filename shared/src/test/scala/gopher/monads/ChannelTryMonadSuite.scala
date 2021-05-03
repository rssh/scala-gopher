package gopher.monadexample

import cps.*
import gopher.*
import munit.*

import scala.concurrent.*
import scala.concurrent.duration.*
import scala.util.*

import cps.monads.FutureAsyncMonad
import gopher.monads.given

class ChannelTryMonadSuite extends FunSuite {

  import scala.concurrent.ExecutionContext.Implicits.global
  given Gopher[Future] = SharedGopherAPI.apply[Future]()


  test("failure inside async with ReadTryChannelCpsMonad") {

     val r = async[[X]=>>ReadChannel[Future,Try[X]]] {
        val ch1 = ReadChannel.fromIterable[Future,Int](1 to 10)
        val x = await(ch1)
        if (x % 3 == 0) then
          throw new RuntimeException("AAA")  
        x
     }

     async[Future] {
        val a1 = r.read()
        val a2 = r.read()
        val a3 = r.read()
        assert(a1 == Success(1))
        assert(a2 == Success(2))
        assert(a3.isFailure) 
     }

  }

  test("using try/catch along with ReadTryChannelCpsMonad") {

     val r = async[[X]=>>ReadChannel[Future,Try[X]]] {
        val ch1 = ReadChannel.fromIterable[Future,Int](1 to 10)
        val x = await(ch1)
        try {
          if (x % 3 == 0) {
             throw (new RuntimeException("AAA"))
          }
          x
        } catch {
          case ex: RuntimeException =>
              100
        }
     }

     async[Future] {
        val a1 = r.read()
        val a2 = r.read()
        val a3 = r.read()
        assert(a1 == Success(1))
        assert(a2 == Success(2))
        assert(a3 == Success(100)) 
     }

  }

}