package gopher.channels


import cps._
import gopher._
import cps.monads.FutureAsyncMonad

import scala.concurrent._
import scala.concurrent.duration._
import scala.util._
import scala.language.postfixOps

import munit._

class ExpireChannelSuite extends FunSuite {


    import scala.concurrent.ExecutionContext.Implicits.global
    given Gopher[Future] = SharedGopherAPI.apply[Future]()
  

    test("if message not readed, it expires") {
        val ch = makeChannel[Int](10).withExpiration(300 milliseconds, false)
        val emptyRead = for {_ <- ch.awrite(1)
             _ <- Time.asleep(400 milliseconds)
             r <- ch.aread
        } yield r

        async {

           try {
               await( emptyRead.withTimeout(300 milliseconds)  )
               assert("Here" == "should not be accessible")
           }catch{
               case ex: TimeoutException =>
                  assert(true)
           }

        }

    }


    test("before expire we can read message") {
        val ch = makeChannel[Int](10).withExpiration(300 milliseconds, false)
        for {
              _ <- ch.awrite(1)
              _ <- Time.asleep(10 milliseconds)
              r <- ch.aread
        } yield assert(r==1)
    }



    test("unbuffered expriew channel: return from write when value expired") {
        val ch = makeChannel[Int](0).withExpiration(300 milliseconds, true)
        ch.awrite(1).withTimeout(2 seconds).transform{
            case Failure(ex) =>
                assert(ex.isInstanceOf[TimeoutException])
                Success(())
            case Success(u) =>
                assert(""=="TimeoutException expected")
                Failure(new RuntimeException())
        }
    }
    

    
    test("expire must be an order") {
        val ch = makeChannel[Int](10).withExpiration(300 milliseconds, false)
        val fr1 = ch.aread
        val fr2 = ch.aread
        for {
            _ <- ch.awriteAll(List(1,2))
            _ <- Time.asleep(10 milliseconds)
            fr3 = ch.aread
            r3 <-  fr3.withTimeout(1 second).transform{
                case Failure(ex: TimeoutException) => 
                    Success(())
                case other => 
                    assert(""==s"TimeoutException expected, we have $other")
                    Failure(new RuntimeException())
            }
            w4 <- ch.awrite(4)
            r31 <- fr3
        } yield assert(r31 == 4)
    }
    

}
