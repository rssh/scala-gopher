package gopher.channels

import org.scalatest.AsyncFunSuite

import scala.concurrent.TimeoutException
import scala.concurrent.duration._
import scala.language.postfixOps

class ExpireChannelSuite extends AsyncFunSuite {

    import CommonTestObjects._

    test("if message not readed, it expires") {
        val ch = gopherApi.make[ExpireChannel[Int]](300 milliseconds, 10)
        val empty = for {_ <- ch.awrite(1)
             _ <- gopherApi.time.asleep(400 milliseconds)
             r <- ch.aread
        } yield r
        recoverToSucceededIf[TimeoutException]{
            empty.withTimeout(300 milliseconds)
        }
    }

    test("before expire we can read message") {
        val ch = gopherApi.make[ExpireChannel[Int]](300 milliseconds, 10)
        for {
              _ <- ch.awrite(1)
              _ <- gopherApi.time.asleep(10 milliseconds)
              r <- ch.aread
        } yield assert(r==1)
    }

    test("unbuffered expriew channel: return from write when value expired") {
        val ch = gopherApi.make[ExpireChannel[Int]](300 milliseconds, 0)
        ch.awrite(1).withTimeout(2 seconds).map(x => assert(x == 1) )
    }

    test("expire must be an order") {
        val ch = gopherApi.make[ExpireChannel[Int]](300 milliseconds, 10)
        val fr1 = ch.aread
        val fr2 = ch.aread
        for {
            _ <- ch.awriteAll(List(1,2))
            _ <- gopherApi.time.asleep(10 milliseconds)
            fr3 = ch.aread
            r3 <- recoverToSucceededIf[TimeoutException]( fr3.withTimeout(1 second) )
            w4 <- ch.awrite(4)
            r31 <- fr3
        } yield assert(r31 == 4)
    }


}
