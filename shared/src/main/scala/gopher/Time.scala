package gopher

import cps._


import scala.concurrent._
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

import scala.language.experimental.macros
import scala.util.Failure

/**
  * Time API, simular to one in golang standard library.
  * @see gopherApi#time
  */
class Time[F[_]](gopherAPI: Gopher[F]) {


    def after(duration: FiniteDuration): ReadChannel[F,FiniteDuration] =
    {
        ???
        /*
        val ch = OneTimeChannel.apply[Instant]()(gopherAPI)
        gopherAPI.actorSystem.scheduler.scheduleOnce(duration){
            ch.awrite(Instant.now())
        }(ec)
        ch
        */
    }

    def asleep(duration: FiniteDuration): F[FiniteDuration] =
    {
        ???
        /*
        val p = Promise[Instant]()
        gopherAPI.actorSystem.scheduler.scheduleOnce(duration){
            p success Instant.now()
        }(ec)
        p.future
        */
    }

    inline def sleep(duration: FiniteDuration): FiniteDuration = 
        given CpsSchedulingMonad[F] = gopherAPI.asyncMonad
        await(asleep(duration))

    /**
      * create ticker. When somebody read this ticker, than one receive duration
      *  messages.  When nobody reading - messages are expired.
      * @param duration
      * @return
      */
    def tick(duration: FiniteDuration): ReadChannel[F,FiniteDuration] =
    {
        ???
     //newTicker(duration)
    }

    /*
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
    */

    def now(): FiniteDuration = 
        FiniteDuration(System.currentTimeMillis(),TimeUnit.MILLISECONDS)

}

object Time:

    /**
     * Used in selector shugar for specyfying tineout.
     *```
     * select{
     *    ......
     *    case t: Time.after if t > expr =>  doSomething
     * }
     *```
     * is a sugar for to selectGroup.{..}.setTimeout(expr, t=>doSomething)
     *@see Select
     **/
    type after = FiniteDuration


    def after[F[_]](duration: FiniteDuration)(using Gopher[F]): ReadChannel[F,FiniteDuration] =
        summon[Gopher[F]].time.after(duration)
