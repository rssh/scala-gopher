package gopher

import cps._
import scala.quoted._
import scala.compiletime._
import scala.concurrent.duration._


/**
 * forever Apply
 **/
class SelectForever[F[_]](api: Gopher[F]) extends SelectGroupBuilder[F,Unit, Unit](api):


  transparent inline def apply(inline pf: PartialFunction[Any,Unit]): Unit =
    ${  
      SelectMacro.foreverImpl('pf,'api)
    }

  transparent inline def applyAsync(inline pf: PartialFunction[Any,Unit]): F[Unit] =
    ${
      SelectMacro.aforeverImpl('pf, 'api)
    }

  def runAsync(): F[Unit] = 
    given CpsSchedulingMonad[F] = api.asyncMonad
    async[F]{
      while{
        val group = api.select.group[Unit]
        try
          groupBuilder(group).run()
          true
        catch
          case ex: ChannelClosedException => 
            false
      } do ()
    }








  






