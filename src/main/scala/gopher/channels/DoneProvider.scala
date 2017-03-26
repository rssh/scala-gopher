package gopher.channels

import gopher._
import scala.concurrent._

trait DoneProvider[A]
{
  val done: Input[A]

  type done = done.read


}



