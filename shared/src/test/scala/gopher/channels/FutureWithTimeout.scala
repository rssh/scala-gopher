package gopher.channels

import gopher._
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.ExecutionContext
import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration


extension [T](f: Future[T]) {

  def withTimeout(d: FiniteDuration)(using gopherApi: Gopher[Future], ec: ExecutionContext): Future[T] =
    val p = Promise[T]
    f.onComplete(p.tryComplete)
    gopherApi.time.schedule({ () =>
        p.tryFailure(new TimeoutException())
    }, d)
    p.future

}

