package gopher

import java.time.Instant

import gopher.channels.{Channel, ExpireChannel, Input, OneTimeChannel}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.util.Failure

/**
  * Time API, simular to one in golang standard library.
  * @see gopherApi#time
  */
class Time(gopherAPI: GopherAPI, ec:ExecutionContext) {


    def after(duration: FiniteDuration): Input[Instant] =
    {
        val ch = OneTimeChannel.apply[Instant]()(gopherAPI)
        gopherAPI.actorSystem.scheduler.scheduleOnce(duration){
            ch.awrite(Instant.now())
        }(ec)
        ch
    }

    def asleep(duration: FiniteDuration): Future[Instant] =
    {
        val p = Promise[Instant]()
        gopherAPI.actorSystem.scheduler.scheduleOnce(duration){
            p success Instant.now()
        }(ec)
        p.future
    }

    def sleep(duration: FiniteDuration): Instant = macro Time.sleepImpl

    /**
      * create ticker. When somebody read this ticker, than one receive LocalDateTime
      *  messages.  When nobody reading - messages are expired.
      * @param duration
      * @return
      */
    def tick(duration: FiniteDuration): Input[Instant] =
    {
     newTicker(duration)
    }

    class Ticker(duration: FiniteDuration)
    {
        val ch = ExpireChannel[Instant](duration,0)(gopherAPI)
        val cancellable = gopherAPI.actorSystem.scheduler.schedule(duration,duration)(tick)(ec)
        gopherAPI.actorSystem.registerOnTermination{
            if (!cancellable.isCancelled) cancellable.cancel()
        }

        def tick():Unit = {
            if (!cancellable.isCancelled) {
                ch.awrite(Instant.now()).onComplete{
                    case Failure(ex:ChannelClosedException) => cancellable.cancel()
                    case Failure(ex) => cancellable.cancel() // strange, but stop.
                    case _ =>
                }(ec)
            }
        }


    }

    def newTicker(duration: FiniteDuration): Channel[Instant] =
    {
        (new Ticker(duration)).ch
    }

    def now() = Instant.now()

}

object Time
{

   def sleepImpl(c:Context)(duration:c.Expr[FiniteDuration]):c.Expr[Instant] =
   {
       import c.universe._
       val r = q"scala.async.Await.await(${c.prefix}.asleep(${duration}))(${c.prefix}.executionContext)"
       c.Expr[Instant](r)
   }

}