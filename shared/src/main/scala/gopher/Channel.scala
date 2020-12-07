package gopher

import cps._
import java.io.Closeable

trait Channel[F[_],W,R] extends WriteChannel[F,W] with ReadChannel[F,R] with Closeable:

  override protected def asyncMonad: CpsAsyncMonad[F]

  //protected override def asyncMonad: CpsAsyncMonad[F]

end Channel

