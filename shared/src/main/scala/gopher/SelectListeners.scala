package gopher

import scala.concurrent.duration.FiniteDuration

trait SelectListeners[F[_],S]:

  def  onRead[A](ch: ReadChannel[F,A]) (f: A => S ): this.type

  def  onWrite[A](ch: WriteChannel[F,A], a: =>A)(f: A => S): this.type

  def  onTimeout(t: FiniteDuration)(f: FiniteDuration => S): this.type


abstract class SelectGroupBuilder[F[_],S](api: Gopher[F]) extends SelectListeners[F,S]:

  protected var  groupBuilder: SelectGroup[F,S] => SelectGroup[F,S] = identity   
 

  def onRead[A](ch: ReadChannel[F,A])(f: A => S): this.type =
    groupBuilder = groupBuilder.andThen{
      g => g.onRead(ch)(f)
    }
    this

  def onReadAsync[A](ch: ReadChannel[F,A])(f: A => F[S]): this.type =
    groupBuilder = groupBuilder.andThen( _.onReadAsync(ch)(f) )
    this

  
  inline def reading[A](ch: ReadChannel[F,A])(f: A=>S): this.type =
    onRead(ch)(f)

  def onWrite[A](ch: WriteChannel[F,A], a: =>A)(f: A=>S): this.type =
    groupBuilder = groupBuilder.andThen{
      g => g.onWrite(ch,a)(f)
    }
    this

  def onWriteAsync[A](ch: WriteChannel[F,A], a: =>A)(f: A=>F[S]): this.type =
    groupBuilder = groupBuilder.andThen{
      g => g.onWriteAsync(ch,a)(f)
    }
    this
    
    
  inline def writing[A](ch: WriteChannel[F,A], a: =>A)(f: A=>S): this.type =
      onWrite(ch,a)(f)
  
    
  def onTimeout(t: FiniteDuration)(f: FiniteDuration => S): this.type =
    groupBuilder = groupBuilder.andThen{
      g => g.onTimeout(t)(f)
    }
    this

  def onTimeoutAsync(t: FiniteDuration)(f: FiniteDuration => F[S]): this.type =
    groupBuilder = groupBuilder.andThen{
        g => g.onTimeoutAsync(t)(f)
    }
    this
  
  
 




 


