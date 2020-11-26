package gopher.time

import java.time.Instant

import org.scalatest.AsyncFunSuite

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * TimeSuite for gopher time API
  */
class TimeSuite  extends AsyncFunSuite {

    import gopher.channels.CommonTestObjects._

    test("ticker must prodice expired time") {
        val accuracy = 50
        val ticker = gopherApi.time.newTicker(100 milliseconds)
        val startTime = Instant.now()
        for { _ <- gopherApi.time.asleep(300 milliseconds)
              r <- ticker.atake(2)
        } yield {
            assert(r(0).toEpochMilli - startTime.toEpochMilli > 200)
            assert(r(1).toEpochMilli - r(0).toEpochMilli >= 100-accuracy)
        }
    }

}
