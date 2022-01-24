package gopher

import cps._
import scala.quoted._
import scala.compiletime._
import scala.concurrent.duration._

import java.util.logging.{Level => LogLevel}


class SelectLoop[F[_]](api: Gopher[F]) extends SelectGroupBuilder[F,Boolean, Unit](api):


  transparent inline def apply(inline pf: PartialFunction[Any,Boolean])(using mc: CpsMonadContext[F]): Unit =
    ${  
      SelectMacro.loopImpl[F]('pf,  'api, 'mc )  
    }
      
  def runAsync(): F[Unit] = 
    given m: CpsSchedulingMonad[F] = api.asyncMonad
    async[F]{
      while{
        val group = api.select.group[Boolean]
        val build = groupBuilder(group)
        val r = build.run()
        r
      } do ()
    }


