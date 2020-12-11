package gopher

import cps._
import java.util.Timer

trait Gopher[F[_]:CpsSchedulingMonad]:

  type Monad[X] = F[X]
  def asyncMonad: CpsSchedulingMonad[F] = summon[CpsSchedulingMonad[F]]

  def makeChannel[A](bufSize:Int = 0): Channel[F,A,A]

  def select: Select[F] =
    new Select[F](this)  

  def time: Time[F] = new Time[F](this)

  
  def timer: Timer


  
def makeChannel[A](bufSize:Int = 1)(using g:Gopher[?]):Channel[g.Monad,A,A] =
      g.makeChannel(bufSize)

def select(using g:Gopher[?]):Select[g.Monad] =
      g.select

