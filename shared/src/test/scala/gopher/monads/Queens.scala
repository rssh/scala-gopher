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
     busyLRDiagonals:Set[Int],
     busyRLDiagonals:Set[Int],
     queens: Vector[(Int,Int)]
  );

  val N = 8

  def  putQueen(state:State): ReadChannel[Future,State] =
    val ch = makeChannel[State]()
    async[Future] {
      val i = state.queens.length
      if i < N then 
        for{    
          j <- 0 until N  if !state.busyColumns.contains(j) &&
                         !state.busyLRDiagonals.contains(i-j) &&
                         !state.busyRLDiagonals.contains(i+j)  
          } {
            val newPos = (i,j)
            val nState = state.copy( busyRows = state.busyRows + i,
                                 busyColumns = state.busyColumns + j,
                                 busyLRDiagonals = state.busyLRDiagonals + (i-j),
                                 busyRLDiagonals = state.busyRLDiagonals + (i+j),
                                 queens = state.queens :+ newPos  )
            ch.write(nState)
        }
      ch.close()
    }
    ch

  def solutions(state: State): ReadChannel[Future,State] =
    async[[X] =>> ReadChannel[Future,X]] {
      if(state.queens.size < N) then
        //println("state:"+state.queens)
        val nextState = await(putQueen(state))
        //println("next-state:"+state.queens)
        await(solutions(nextState))
      else
        state    
    }

  val emptyState = State(Set.empty, Set.empty, Set.empty, Set.empty, Vector.empty)
                        
  test("two first solution for 8 queens problem") {
     async[Future] {
       val r = solutions(emptyState).take(2)
       assert(!r.isEmpty)
       println(r.map(_.queens))
     }
  }
    

}