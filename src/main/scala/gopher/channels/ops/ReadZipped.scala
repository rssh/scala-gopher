package gopher.channels.ops

import scala.concurrent._
import gopher.channels._

class ReadZipped[A,B](it: Iterator[B],f:(A,B)=>Unit) extends PlainReadAction[A] {

  override def plainApply(in: ReadActionInput[A]):Boolean =
  {
    if (it.isEmpty) {
       in.tie.shutdown()
       false
    } else {
       val x = it.next
       System.err.println("it="+it+", after next="+x)
       f(in.value,x)
       true
    }
  }
  
}

