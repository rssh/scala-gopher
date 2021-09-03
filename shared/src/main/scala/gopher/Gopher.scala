package gopher

import cps._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.util._

import java.util.logging.{Level => LogLevel}
import java.util.concurrent.Executor


/**
 * core of GopherAPI.
 *Given instance of Gopher[F] need for using most of Gopher operations.
 **/
trait Gopher[F[_]:CpsSchedulingMonad]:

  type Monad[X] = F[X]
  def asyncMonad: CpsSchedulingMonad[F] = summon[CpsSchedulingMonad[F]]

  def makeChannel[A](bufSize:Int = 0,
                    autoClose: Boolean = false): Channel[F,A,A]                  

  def makeOnceChannel[A](): Channel[F,A,A] =
                    makeChannel[A](1,true)                   

  def select: Select[F] =
    new Select[F](this)  

  def time: Time[F] 

  def setLogFun(logFun:(LogLevel, String, Throwable|Null) => Unit): ((LogLevel, String, Throwable|Null) => Unit)

  def log(level: LogLevel, message: String, ex: Throwable| Null): Unit

  def log(level: LogLevel, message: String): Unit =
    log(level,message, null)

  def taskExecutionContext: ExecutionContext

  protected[gopher] def logImpossible(ex: Throwable): Unit =
    log(LogLevel.WARNING, "impossible", ex)

  protected[gopher] def spawnAndLogFail[T](op: =>F[T]): F[Unit] =
     asyncMonad.mapTry(asyncMonad.spawn(op)){
           case Success(_) => ()
           case Failure(ex) =>
             log(LogLevel.WARNING, "exception in spawned process", ex)
             ()
     }

  
  
def makeChannel[A](bufSize:Int = 0, 
                  autoClose: Boolean = false)(using g:Gopher[?]):Channel[g.Monad,A,A] =
      g.makeChannel(bufSize, autoClose)

def makeOnceChannel[A]()(using g:Gopher[?]): Channel[g.Monad,A,A] =
      g.makeOnceChannel[A]()                   

def select(using g:Gopher[?]):Select[g.Monad] =
      g.select

def futureInput[F[_],A](f: F[A])(using g: Gopher[F]): ReadChannel[F,A] =
      val ch = g.makeOnceChannel[Try[A]]()
      g.spawnAndLogFail{
            g.asyncMonad.flatMapTry(f)(r => ch.awrite(r))
      } 
      ch.map(_.get)

extension [F[_],A](fa: F[A])(using g: Gopher[F])
      def asChannel : ReadChannel[F,A] =
            futureInput(fa)

extension [F[_],A](c: IterableOnce[A])(using g: Gopher[F])
      def asReadChannel: ReadChannel[F,A] =
            ReadChannel.fromIterable(c)