package gopher

import java.time.LocalDateTime

import gopher.channels.{Channel, Input, OneTimeChannel}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.FiniteDuration
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

/**
  * Time API
  *
  * @see gopherApi#time
  */
class Time(gopherAPI: GopherAPI, ec:ExecutionContext) {

    implicit def executionContext = ec

    def after(duration: FiniteDuration): Input[LocalDateTime] =
    {
        val ch = OneTimeChannel.apply[LocalDateTime]()(gopherAPI)
        gopherAPI.actorSystem.scheduler.scheduleOnce(duration){
            ch.awrite(LocalDateTime.now())
        }
        ch
    }

    def asleep(duration: FiniteDuration): Future[LocalDateTime] =
    {
        val p = Promise[LocalDateTime]()
        gopherAPI.actorSystem.scheduler.scheduleOnce(duration){
            p success LocalDateTime.now()
        }
        p.future
    }

    def sleep(duration: FiniteDuration): LocalDateTime = macro Time.sleepImpl

    def tick(duration: FiniteDuration): Input[LocalDateTime] =
    {
     newTicker(duration)
    }

    def newTicker(duration: FiniteDuration): Channel[LocalDateTime] =
    {
        val ch = gopherAPI.makeChannel[LocalDateTime]()
        gopherAPI.actorSystem.scheduler.schedule(duration,duration) {
            //TODO:  make expired.
            ch.awrite(LocalDateTime.now())
        }
        ch
    }


}

object Time
{

   def sleepImpl(c:Context)(duration:c.Expr[FiniteDuration]):c.Expr[LocalDateTime] =
   {
       import c.universe._
       val r = q"scala.async.Await.await(${c.prefix}.asleep(${duration}))(${c.prefix}.executionContext)"
       c.Expr[LocalDateTime](r)
   }

}