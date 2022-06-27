package gopher.monadexample
import cps.*
import gopher.*
import munit.*

import scala.concurrent.*
import scala.concurrent.duration.*
import scala.collection.SortedSet

import cps.monads.FutureAsyncMonad
import gopher.monads.given


class QueensSuite extends FunSuite {

  import scala.concurrent.ExecutionContext.Implicits.global
  given Gopher[Future] = SharedGopherAPI.apply[Future]()

  type State = Vector[(Int,Int)]
     
  extension(queens:State) {

    def isUnderAttack(i:Int, j:Int): Boolean = 
      queens.exists{ (qi,qj) => 
        qi == i || qj == j || i-j == qi-qj || i+j == qi+qj
      }
      
    def put(i:Int, j:Int): State =
     queens :+ (i,j) 

  }

  val N = 8

  def  putQueen(state:State): ReadChannel[Future,State] =
    val ch = makeChannel[State]()
    async[Future] {
      val i = state.length
      if i < N then 
        for{ j <- 0 until N  if !state.isUnderAttack(i,j) } 
          ch.write(state.put(i,j))
      ch.close()
    }
    ch

  def solutions(state: State): ReadChannel[Future,State] =
    async[[X] =>> ReadChannel[Future,X]] {
      if(state.size < N) then
        val nextState = await(putQueen(state))
        await(solutions(nextState))
      else
        state    
    }

                        
  test("two first solution for 8 queens problem") {
     async[Future] {
       val r = solutions(Vector.empty).take(2)
       assert(!r.isEmpty)
       println(r)
     }
  }
    

}
