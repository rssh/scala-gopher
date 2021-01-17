package gopher.channels

import gopher._
import munit._

class IOComposeSuite extends FunSuite {


    test("simple composition of IO with map") {
        import gopherApi._

        val ch = makeChannel[Int]()
        val ch1 = makeChannel[Int]()
        val chs = ch1.map( _.toString )

        val pipeline = ch |> chs

        pipeline.awriteAll(List(10,12,34,43))

        for{
            r1 <- pipeline.aread
            r2 <- pipeline.aread
            r3 <- pipeline.aread
            r4 <- pipeline.aread
        } yield assert((r1,r2,r3,r4) == ("10","12","34","43") )

    }


    test("simple double composition of IO with map") {
        import gopherApi._

        val ch = makeChannel[Int]()
        val chs = makeChannel[Int]().map( _.toString )
        val reverse = makeChannel[String]().map(_.reverse.toInt)

        val pipeline = ch |> chs |> reverse

        pipeline.awriteAll(List(10,12,34,43))

        for{
            r1 <- pipeline.aread
            r2 <- pipeline.aread
            r3 <- pipeline.aread
            r4 <- pipeline.aread
        } yield assert((r1,r2,r3,r4) == (1,21,43,34) )

    }

    test("closing channel must close it's composition") {
        import gopherApi._

        //pending

        val ch = makeChannel[Int]()
        val ch1 = makeChannel[Int]()

        val composed = ch |> ch1

        val f1 = for{
            _ <- ch.awrite(1)
        } yield ch.close()

        for{
            x1 <- composed.aread
            _ = assert(x1==1)
            x2 <- recoverToSucceededIf[ChannelClosedException](composed.aread)
        } yield x2

    }


    val gopherApi = CommonTestObjects.gopherApi

}
