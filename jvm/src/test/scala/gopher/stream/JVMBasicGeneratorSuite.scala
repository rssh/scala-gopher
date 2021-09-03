package gopher.stream

import scala.concurrent.*
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.Implicits.global

import cps.*
import cps.monads.given

import gopher.*
import gopher.util.Debug
import java.util.logging.{Level => LogLevel}


import munit.*


class JVMBasicGeneratorSuite extends FunSuite {

  val N = 10000

  given Gopher[Future] = SharedGopherAPI[Future]()

  val inMemoryLog = new Debug.InMemoryLog()
  

  summon[Gopher[Future]].setLogFun( Debug.inMemoryLogFun(inMemoryLog) )
  
  
  test("M small loop in gopher ReadChannel") {

     val M = 1000
     val N = 100

     val folds: Seq[Future[Int]] = for(k <- 1 to M) yield {
        val channel = asyncStream[ReadChannel[Future,Int]] { out =>
          var last = 0
          for(i <- 1 to N) {
              out.emit(i)
              last = i
              //println("emitted: "+i)
              //summon[Gopher[Future]].log(LogLevel.FINE, s"emitted $i in $k")
          }
          summon[Gopher[Future]].log(LogLevel.FINE, s"last $last in $k")
        }
        async[Future]{
          channel.fold(0)(_ + _)
        }
     }

     val expected = (1 to N).sum
 
     
     val f = folds.foldLeft(Future.successful(())){ (s,e) =>
        s.flatMap{ r =>
            e.map{ x =>
              assert(x == expected)
        }  }
     }

     try {
        val r = Await.result(f, 30.seconds);
      }catch{
        case ex: Throwable =>  //: TimeoutException =>
          Debug.showTraces(20)
          println("---")
          Debug.showInMemoryLog(inMemoryLog)
          throw ex
      }
     

  }



}