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

  case class State(
     busyRows:Set[Int],
     busyColumns:Set[Int],
     busyDiagonals:Set[Int],
     queens: Set[(Int,Int)]
  );

  val N = 8

  def  putQueen(state:State): ReadChannel[Future,State] =
    val ch = makeChannel[State]()
    async[Future] {
      for{ 
        i <- 0 until N  if !state.busyRows.contains(i)  
        j <- 0 until N  if !state.busyColumns.contains(j) &&
                         !(state.busyDiagonals.contains(i-j)) 
        } {
          val newPos = (i,j)
          val nState = state.copy( busyRows = state.busyRows + i,
                                 busyColumns = state.busyColumns + j,
                                 busyDiagonals = state.busyDiagonals + (i-j),
                                 queens = state.queens + newPos  )
          ch.write(nState)
      }
      ch.close()
    }
    ch

  def solutions(state: State): ReadChannel[Future,State] =
    async[[X] =>> ReadChannel[Future,X]] {
      if(state.queens.size < 8) then
        val nextState = await(putQueen(state))
        await(solutions(nextState))
      else
        state    
    }

  val emptyState = State(Set.empty, Set.empty, Set.empty, Set.empty)
                        
  test("first solution for 8 queens problem") {
     async[Future] {
       val r = solutions(emptyState).take(1)
       assert(!r.isEmpty)
       println(r.head.queens)
     }
  }
    


}