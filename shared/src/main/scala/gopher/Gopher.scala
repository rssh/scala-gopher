package gopher

import cps._
import scala.concurrent.duration.Duration


trait Gopher[F[_]:CpsSchedulingMonad]:

  type Monad[X] = F[X]
  def asyncMonad: CpsSchedulingMonad[F] = summon[CpsSchedulingMonad[F]]

  def makeChannel[A](bufSize:Int = 0,
                    autoClose: Boolean = false, 
                    expire: Duration = Duration.Inf): Channel[F,A,A]                  

  def makeOnceChannel[A](): Channel[F,A,A] =
                    makeChannel[A](1,true)                   

  def select: Select[F] =
    new Select[F](this)  

  def time: Time[F] 
  
  
def makeChannel[A](bufSize:Int = 0, 
                  autoClose: Boolean = false,
                  expire: Duration = Duration.Inf)(using g:Gopher[?]):Channel[g.Monad,A,A] =
      g.makeChannel(bufSize, autoClose, expire)

def makeOnceChannel[A]()(using g:Gopher[?]): Channel[g.Monad,A,A] =
      g.makeOnceChannel[A]()                   


def select(using g:Gopher[?]):Select[g.Monad] =
      g.select

