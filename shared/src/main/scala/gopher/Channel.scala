package gopher

import cps._

trait IOChannel[F[_]:CpsAsyncMonad,I,O] extends IChannel[F,I] with OChannel[F,O]:

  protected def m: CpsAsyncMonad[F] = summon[CpsAsyncMonad[F]]

end IOChannel

trait Channel[F[_]:CpsAsyncMonad,A] extends IOChannel[F,A,A]:


end Channel
