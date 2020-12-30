package gopher

import scala.concurrent.duration.FiniteDuration

trait SelectListeners[F[_],S]:

  def  onRead[A](ch: ReadChannel[F,A]) (f: A => S ): this.type

  def  onWrite[A](ch: WriteChannel[F,A], a: =>A)(f: A => S): this.type

  def  onTimeout(t: FiniteDuration)(f: FiniteDuration => S): this.type


 


