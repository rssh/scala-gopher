package gopher

import cps._
import scala.quoted._
import scala.compiletime._
import scala.concurrent.duration._

class SelectLoop[F[_]:CpsSchedulingMonad](api: Gopher[F]) extends SelectListeners[F,Boolean]:

  private var  groupBuilder: SelectGroup[F,Boolean] => SelectGroup[F,Boolean] = identity   

  def onRead[A](ch: ReadChannel[F,A])(f: A => Boolean): this.type =
    groupBuilder = groupBuilder.andThen{
      g => g.onRead(ch)(f)
    }
    this

  // TODO: think about special notation for builders
  def onReadAsync[A](ch: ReadChannel[F,A])(f: A => F[Boolean]): this.type =
    groupBuilder = groupBuilder.andThen( _.onReadAsync(ch)(f) )
    this

  // TODO: think about special notation for builders
  def onRead_async[A](ch: ReadChannel[F,A])(f: A => F[Boolean]): F[this.type] =
    summon[CpsMonad[F]].pure(onReadAsync(ch)(f))
  
  inline def reading[A](ch: ReadChannel[F,A])(f: A=>Boolean): this.type =
    onRead(ch)(f)

  def onWrite[A](ch: WriteChannel[F,A], a: =>A)(f: A=>Boolean): this.type =
    groupBuilder = groupBuilder.andThen{
      g => g.onWrite(ch,a)(f)
    }
    this

  def onWriteAsync[A](ch: WriteChannel[F,A], a: =>A)(f: A=>F[Boolean]): this.type =
    groupBuilder = groupBuilder.andThen{
      g => g.onWriteAsync(ch,a)(f)
    }
    this
    
  def onWrite_async[A](ch: WriteChannel[F,A], fa: ()=>F[A])(f: A=>F[Boolean]): F[this.type] =
    api.asyncMonad.map(fa())(a => onWriteAsync(ch,a)(f))
    
  inline def writing[A](ch: WriteChannel[F,A], a: =>A)(f: A=>Boolean): this.type =
      onWrite(ch,a)(f)
  
    
  def onTimeout(t: FiniteDuration)(f: FiniteDuration => Boolean): this.type =
    groupBuilder = groupBuilder.andThen{
      g => g.onTimeout(t)(f)
    }
    this

  def onTimeoutAsync(t: FiniteDuration)(f: FiniteDuration => F[Boolean]): this.type =
    groupBuilder = groupBuilder.andThen{
        g => g.onTimeoutAsync(t)(f)
    }
    this
  
    
  def onTimeout_async(t: FiniteDuration)(f: FiniteDuration => F[Boolean]): F[this.type] =
    summon[CpsMonad[F]].pure(onTimeoutAsync(t)(f))

  inline def apply(inline pf: PartialFunction[Any,Boolean]): Unit =
    ${  
      Select.loopImpl[F]('pf, '{summonInline[CpsSchedulingMonad[F]]}, 'api )  
    }    
  


  def runAsync(): F[Unit] = async[F] {
    try
      while{
        val group = api.select.group[Boolean]
        val r = groupBuilder(group).run()
        r
      } do ()
    catch
      case ex:Throwable =>
        // TODO: log
        ex.printStackTrace() 
  }

  inline def run(): Unit = await(runAsync())

   
  


