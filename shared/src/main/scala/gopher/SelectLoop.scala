package gopher

import cps._
import scala.concurrent.duration._

class SelectLoop[F[_]:CpsSchedulingMonad](api: Gopher[F]):

  private var  groupBuilder: SelectGroup[F,Boolean] => SelectGroup[F,Boolean] = identity   

  def onRead[A](ch: ReadChannel[F,A])(f: A => Boolean): this.type =
    groupBuilder = groupBuilder.andThen{
      g => g.onRead(ch)(f)
    }
    this

  // TODO: think about special notation for builders
  def onRead_async[A](ch: ReadChannel[F,A])(f: A => F[Boolean]): F[this.type] =
    groupBuilder = groupBuilder.andThen{
      g => {
        g.onRead_async(ch)(f)
        g
      }
    }
    summon[CpsMonad[F]].pure(this)
  

  def onWrite[A](ch: WriteChannel[F,A], a: =>A)(f: A=>Boolean): this.type =
    groupBuilder = groupBuilder.andThen{
      g => g.onWrite(ch,a)(f)
    }
    this

  def onWrite_async[A](ch: WriteChannel[F,A], a: =>A)(f: A=>F[Boolean]): F[this.type] =
    groupBuilder = groupBuilder.andThen{
        g => 
          g.onWrite_async(ch,a)(f)
          g 
    }
    summon[CpsMonad[F]].pure(this)
  
    
  def onTimeout(t: FiniteDuration)(f: FiniteDuration => Boolean): this.type =
    groupBuilder = groupBuilder.andThen{
      g => g.onTimeout(t)(f)
    }
    this

  def onTimeout_async(t: FiniteDuration)(f: FiniteDuration => F[Boolean]): F[this.type] =
    groupBuilder = groupBuilder.andThen{
      g => g.onTimeout_async(t)(f)
           g
    }
    summon[CpsMonad[F]].pure(this)
    
  def runAsync(): F[Unit] = async[F] {
    while{
      val group = api.select.group[Boolean]
      groupBuilder(group).run()
    } do () 
  }

  inline def run(): Unit = await(runAsync())

   



