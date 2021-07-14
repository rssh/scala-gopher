package gopher

import cps._
import scala.quoted._
import scala.compiletime._
import scala.concurrent.duration._


/**
 * Result of `select.forever`: apply method accept partial pseudofunction which evalueated forever.
 **/
class SelectForever[F[_]](api: Gopher[F]) extends SelectGroupBuilder[F,Unit, Unit](api):


  transparent inline def apply(inline pf: PartialFunction[Any,Unit]): Unit =
    ${  
      SelectMacro.foreverImpl('pf,'api)
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








  






