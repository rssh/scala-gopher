package gopher.stream

import scala.concurrent.*
import scala.concurrent.ExecutionContext.Implicits.global

import cps.*
import cps.monads.given

import gopher.*
import gopher.util.Debug

import munit.*


class BasicGeneratorSuite extends FunSuite {

  val N = 10000

  given Gopher[Future] = SharedGopherAPI[Future]()

  val inMemoryLog = new Debug.InMemoryLog()
  

  summon[Gopher[Future]].setLogFun( Debug.inMemoryLogFun(inMemoryLog) )
  
  test("simple loop in gopher ReadChannel") {

     val channel = asyncStream[ReadChannel[Future,Int]] { out =>
       for(i <- 1 to N) {
         out.emit(i)
       }
     }
 

     async[Future] {
        val r = channel.fold(0)(_ + _)
        assert(r == (1 to N).sum)
     }

  }

  test("M small loop in gopher ReadChannel") {

     val M = 1000
     val N = 100

     val folds: Seq[Future[Int]] = for(i <- 1 to M) yield {
        val channel = asyncStream[ReadChannel[Future,Int]] { out =>
          for(i <- 1 to N) {
              out.emit(i)
              //println("emitted: "+i)
          }
        }
        async[Future]{
          channel.fold(0)(_ + _)
        }
     }

     val expected = (1 to N).sum
 
     
     folds.foldLeft(Future.successful(())){ (s,e) =>
        s.flatMap{ r =>
            e.map{ x =>
              assert(x == expected)
        }  }
     }

    
  }

  test("exception should break loop in gopher generator") {
    val channel = asyncStream[ReadChannel[Future, Int]] { out =>
      for(i <- 1 to N) {
        if (i == N/2) then
          throw new RuntimeException("bye")
        out.emit(i)
      }
    }

    val res = async[Future] {
        val r = channel.fold(0)(_ + _)
        assert(r == (1 to N).sum)
    }
      
    res.failed.map(ex => assert(ex.getMessage()=="bye"))
    
  }



}