package gopher

import cps._

trait Channel[F[_],W,R] extends WriteChannel[F,W] with ReadChannel[F,R]:

  override protected def asyncMonad: CpsAsyncMonad[F]

  //protected override def asyncMonad: CpsAsyncMonad[F]


end Channel

