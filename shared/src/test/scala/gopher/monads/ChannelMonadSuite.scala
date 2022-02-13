package gopher.monadexample

import cps.*
import gopher.*
import munit.*

import scala.concurrent.*
import scala.concurrent.duration.*
import scala.collection.SortedSet

import cps.monads.{given,*}
import gopher.monads.{given,*}

class ChannelMonadSuite extends FunSuite {

  import scala.concurrent.ExecutionContext.Implicits.global
  given Gopher[Future] = SharedGopherAPI.apply[Future]()


  test("using channel as monad and read inside") {

       
       val chX = ReadChannel.fromValues[Future,Int](1,2,3,4,5)
       val chY = ReadChannel.fromValues[Future,Int](1,2,3,4,5)

       
       val squares: ReadChannel[Future,Int] = async[[X] =>> ReadChannel[Future,X]] { 
        val x: Int = await(chX)
        //println(s"reading from X $x")
        val y: Int = chY.read()
        //println(s"reading from Y  $y")
        x*y
       }
       

       
       async[Future] {
         val a1 = squares.read()
         //println(s"a1==${a1}")
         assert(a1 == 1)
         val a2 = squares.read()
         //println(s"a2==${a2}")
         assert(a2 == 4)
         val a3 = squares.read()
         assert(a3 == 9)
         val a4 = squares.read()
         assert(a4 == 16)
         val a5 = squares.read()
         assert(a5 == 25)
       }

  }
  

  test("using channel with flatMap") {

    val chX = ReadChannel.fromValues(1,2,3,4,5)

    val r = async[[X] =>> ReadChannel[Future,X]] {
      val x = await(chX)
      val fy = if (x %2 == 0) ReadChannel.empty[Future,Int] else ReadChannel.fromIterable(1 to x)
      await(fy)*x
    }

    async[Future] {
      val seq = r.take(20)
      //println(seq)
      assert(seq(0)==1)
      assert(seq(1)==3)
      assert(seq(2)==6)
      assert(seq(3)==9)
      assert(seq(4)==5)
      assert(seq(5)==10)
    }
              

  }

  
  test("sieve inside channel monad") {

    val n = 100
    val r = async[[X] =>> ReadChannel[Future,X]] {
      var initial = ReadChannel.fromIterable[Future,Int](2 to n)
      val drop = ReadChannel.empty[Future,Int]
      var prevs = IndexedSeq.empty[Int]
      var done = false
      val x = await(initial)
      if !prevs.isEmpty then
          var pi = 0  
          while{
            var p = prevs(pi)
            if (x % p == 0) then
              val r = await(drop)
            pi = pi+1
            p*p < x
          } do ()
      prevs = prevs :+ x
      x
    }

    async[Future] {
      val primes = r.take(20)
      //println(s"primes: $primes")
      assert(primes(0)==2)
      assert(primes(1)==3)
      assert(primes(2)==5)
      assert(primes(3)==7)
      assert(primes(6)==17)
      assert(primes(9)==29)
    }


  }


}
