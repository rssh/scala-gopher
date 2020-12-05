package gopher

import cps._

trait Gopher[F[_]:CpsSchedulingMonad]:

  type Monad[X] = F[X]

  def makeChannel[A](bufSize:Int = 1): Channel[F,A,A]

  
def makeChannel[A](bufSize:Int = 1)(using g:Gopher[?]):Channel[g.Monad,A,A] =
      g.makeChannel(bufSize)

