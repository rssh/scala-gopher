package gopher.channels.ops

import scala.concurrent._
import gopher.channels._

class ReadWhile[A](p: A => Boolean, f: A => Unit) extends PlainReadAction[A] {

  override def plainApply(in: ReadActionInput[A]): Boolean =
  {
    val toContinue = p(in.value)
    if (toContinue){
           f(in.value)
    } else {
           in.tie.shutdown()
    }
    toContinue
  }

  
}