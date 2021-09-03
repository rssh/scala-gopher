package gopher

import cps._
import scala.annotation._
import scala.concurrent._
import scala.concurrent.duration._
import scala.util._
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

import java.util.logging.{Level => LogLevel}



class DuppedInput[F[_],A](origin:ReadChannel[F,A], bufSize: Int=1)(using api:Gopher[F])
{

  def pair = (sink1, sink2)

  val sink1 = makeChannel[A](bufSize,false)
  val sink2 = makeChannel[A](bufSize,false)

  given CpsSchedulingMonad[F] = api.asyncMonad

  val runner = async{
    while
      origin.optRead() match
        case Some(a) => 
          sink1.write(a)
          sink2.write(a)
          true
        case None =>
          false
    do ()
    sink1.close()
    sink2.close()  
  }   

  api.spawnAndLogFail(runner)

}
