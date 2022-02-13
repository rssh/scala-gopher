package gopher

import cps._

import scala.quoted._
import scala.compiletime._
import scala.concurrent.duration._

/** Organize waiting for read/write from multiple async channels
  *  
  *  Gopher[F] provide a function `select` of this type.
  */
class Select[F[_]](api: Gopher[F]):

  /** wait until some channels from the list in <code> pf </code>.
   *
   *```Scala
   *async{
   *  ....  
   *  select {
   *    case vx:xChannel.read => doSomethingWithX 
   *    case vy:yChannel.write if (vy == valueToWrite) => doSomethingAfterWrite(vy)
   *    case t: Time.after if (t == 1.minute) => processTimeout
   *  }
   *  ...
   *}
   *```
   */
  transparent inline def apply[A](inline pf: PartialFunction[Any,A])(using mc:CpsMonadContext[F]): A =
    ${  
      SelectMacro.onceImpl[F,A]('pf, 'api, 'mc )  
     }    

  /**
   * create select groop
   *@see [gopher.SelectGroup]
   **/   
  def group[S]: SelectGroup[F,S] = new SelectGroup[F,S](api)   

  def once[S]: SelectGroup[F,S] = new SelectGroup[F,S](api)   

  /**
   * create Select Loop.
   **/
  def loop: SelectLoop[F] = new SelectLoop[F](api)

    
  def fold[S](s0:S)(step: S => S | SelectFold.Done[S]): S = {
    var s: S = s0
    while{
        step(s) match
          case SelectFold.Done(r) =>
            s = r.asInstanceOf[S]
            false
          case other =>
            s = other.asInstanceOf[S]
            true
    } do ()
    s
  }

  def fold_async[S](s0:S)(step: S => F[S | SelectFold.Done[S]]): F[S] = {
    api.asyncMonad.flatMap(step(s0)){ s =>
      s match 
        case SelectFold.Done(r) => api.asyncMonad.pure(r.asInstanceOf[S])
        case other => fold_async[S](other.asInstanceOf[S])(step)
    }
  }

  transparent inline def afold[S](s0:S)(inline step: CpsMonadContext[F] ?=> S => S | SelectFold.Done[S]) : F[S] =
    given CpsAsyncMonad[F] = api.asyncMonad
    async[F]{ 
      fold(s0)(step)
    }

  def afold_async[S](s0:S)(step: S => F[S | SelectFold.Done[S]]) : F[S] =
    fold_async(s0)(step)

      
  def map[A](step: SelectGroup[F,A] => A): ReadChannel[F,A] =
    mapAsync[A](x => api.asyncMonad.pure(step(x)))

  def mapAsync[A](step: SelectGroup[F,A] => F[A]): ReadChannel[F,A] =
    val r = makeChannel[A]()(using api)
    given CpsSchedulingMonad[F] = api.asyncMonad
    api.spawnAndLogFail{
      async{
        var done = false
        while(!done) 
          val g = SelectGroup[F,A](api)
          try {
            val e = await(step(g))
            r.write(e)
          } catch { 
            case ex: ChannelClosedException =>
              r.close()
              done=true
          }
      }
    }
    r

  /**
   *  create forever runner.
   **/  
  def forever: SelectForever[F] = new SelectForever[F](api)

  /**
   * run forever expression in `pf`,  return 
   **/
  transparent inline def aforever(inline pf: PartialFunction[Any,Unit]): F[Unit] =
    ${  SelectMacro.aforeverImpl('pf, 'api)  }

  


