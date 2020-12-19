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

  val runner = SelectLoop[F](api)(using api.asyncMonad).onRead(origin){a => 
    val f1 = sink1.awrite(a)
    val f2 = sink2.awrite(a)
    true
  }.onRead(origin.done){ _ => 
    sink1.close()
    sink2.close()
    false
  }.runAsync()

}
