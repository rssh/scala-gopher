package gopher.channels.ops

import scala.concurrent._
import gopher.channels._

class FoldWhile[A,S](s0: S, p: (A,S)=>Boolean, f:(A,S)=>S, promise: Promise[S]) extends PlainReadAction[A]
{
  
  private[this] var s = s0;

  override def plainApply(in: ReadActionInput[A]): Boolean =
  {
    val toContinue = p(in.value, s)
    if (toContinue){
           s=f(in.value,s)
    } else {
           in.tie.shutdown()
           promise.success(s)
    }
    toContinue
  }

  
}