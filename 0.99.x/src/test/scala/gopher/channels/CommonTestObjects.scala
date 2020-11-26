package gopher.channels

import java.util.concurrent.TimeoutException

import akka.actor._
import gopher._

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.FiniteDuration

object CommonTestObjects {

    lazy val actorSystem = ActorSystem.create("system")
    lazy val gopherApi = Gopher(actorSystem)

    implicit class FutureWithTimeout[A](f: Future[A])
    {
        import scala.concurrent.ExecutionContext.Implicits.global
        def withTimeout(timeout:FiniteDuration):Future[A]=
        {
            val p = Promise[A]()
            f.onComplete(p.tryComplete)
            actorSystem.scheduler.scheduleOnce(timeout){
                p.tryFailure(new TimeoutException())
            }
            p.future
        }
    }

}
