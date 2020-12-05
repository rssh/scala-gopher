package gopher

import cps._

trait Channel[F[_],W,R] extends ReadChannel[F,R] with WriteChannel[F,W]:

  override protected def asyncMonad: CpsAsyncMonad[F]

  //protected override def asyncMonad: CpsAsyncMonad[F]


end Channel

