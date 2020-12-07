package gopher.channels

import cps._
import cps.monads.FutureAsyncMonad
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import gopher._

import munit._

class AsyncChannelTests extends FunSuite {


    val gopherApi = SharedGopherAPI[Future]()
    val MAX_N = 100

    test("async base: channel write, channel read") {

        val channel = gopherApi.makeChannel[Int](10)
        channel.awriteAll(1 to MAX_N)
        
        val consumer = async{
          var sum = 0
          while{val a = channel.read
                sum += a 
                a < MAX_N
          } do ()
          sum
        }

        consumer.map{ s =>
           assert(s == (1 to MAX_N).sum)
        }

    }

}