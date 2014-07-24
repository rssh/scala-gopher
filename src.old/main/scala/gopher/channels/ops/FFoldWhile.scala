package gopher.channels.ops

import scala.concurrent._
import gopher.channels._

class FFoldWhile[A,S](s0: S, p: (A,S)=>Boolean, f:(A,S)=>Future[S], promise: Promise[S]) extends ReadAction[A]
{
  
  private[this] var s = s0;

  override def apply(in: ReadActionInput[A]): Option[Future[ReadActionOutput]] =
  {
    val toContinue = p(in.value, s)
    import in.tie.executionContext
    Some(if (toContinue){
           f(in.value,s) map { s1 =>
              s=s1;
              ReadActionOutput(true)
           }
    } else {
           in.tie.shutdown()
           promise.success(s)
           Promise.successful(ReadActionOutput(false)).future
    })
  }

  
}

