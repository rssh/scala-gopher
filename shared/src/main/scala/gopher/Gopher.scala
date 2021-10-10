package gopher

import cps._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.util._

import java.util.logging.{Level => LogLevel}
import java.util.concurrent.Executor


/**
 * core of Gopher API. Given instance of Gopher[F] need for using most of Gopher operations.
 * 
 * Gopher is a framework, which implements CSP (Communication Sequence Process).
 * Process here - scala units of execution (i.e. functions, blok of code, etc). 
 * Communication channels represented by [gopher.Channel]
 * 
 * @see [gopher.Channel]
 * @see [gopher#select]
 **/
trait Gopher[F[_]:CpsSchedulingMonad]:

  type Monad[X] = F[X]
  
  /**
   * Monad which control asynchronic execution.
   * The main is scheduling: i.e. ability to submit monadic expression to scheduler
   * and know that this monadic expression will be evaluated.
   **/
  def asyncMonad: CpsSchedulingMonad[F] = summon[CpsSchedulingMonad[F]]

  /**
   * Create Read/Write channel. 
   * @param bufSize - size of buffer. If it is zero, the channel is unbuffered. (i.e. writer is blocked until reader start processing).
   * @param autoClose - close after first message was written to channel.
   * @see [gopher.Channel]
   **/
  def makeChannel[A](bufSize:Int = 0,
                    autoClose: Boolean = false): Channel[F,A,A]                  

  /**
   * Create channel where you can write only one element.
   * @see [gopher.Channel]
   **/                  
  def makeOnceChannel[A](): Channel[F,A,A] =
                    makeChannel[A](1,true)                   

  /***
   *Create a select statement, which used for choosing one action from a set of potentially concurrent asynchronics events.
   *[@see gopher.Select] 
   **/
  def select: Select[F] =
    new Select[F](this)  

  /**
   * get an object with time operations.
   **/  
  def time: Time[F] 

  /**
   * set logging function, which output internal diagnostics and errors from spawned processes.
   **/
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

end Gopher
  

/**
* Create Read/Write channel. 
* @param bufSize - size of buffer. If it is zero, the channel is unbuffered. (i.e. writer is blocked until reader start processing).
* @param autoClose - close after first message was written to channel.
* @see [gopher.Channel]
**/
def makeChannel[A](bufSize:Int = 0, 
                  autoClose: Boolean = false)(using g:Gopher[?]):Channel[g.Monad,A,A] =
      g.makeChannel(bufSize, autoClose)
      

def makeOnceChannel[A]()(using g:Gopher[?]): Channel[g.Monad,A,A] =
      g.makeOnceChannel[A]()                   


def select(using g:Gopher[?]):Select[g.Monad] =
      g.select

/**
 * represent `F[_]` as read channel.
 **/      
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