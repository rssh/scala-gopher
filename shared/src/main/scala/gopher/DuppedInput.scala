package gopher

import cps._
import scala.annotation._
import scala.concurrent._
import scala.util._
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger




class DuppedInput[F[_],A](origin:ReadChannel[F,A])(using api:Gopher[F])
{

  def pair = (sink1, sink2)

  val sink1 = makeChannel[A](1)
  val sink2 = makeChannel[A](1)

  given CpsSchedulingMonad[F] = api.asyncMonad

  val runner = SelectLoop[F](api).onReadAsync(origin){a => async{
    val f1 = sink1.write(a)
    val f2 = sink2.write(a)
    true
  }}.onRead(origin.done){ _ => 
    sink1.close()
    sink2.close()
    false
  }.runAsync()

}
