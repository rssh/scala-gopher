package gopher

import cps._
import scala.quoted._
import scala.compiletime._
import scala.concurrent.duration._


class SelectForever[F[_]:CpsSchedulingMonad](api: Gopher[F]) extends SelectGroupBuilder[F,Unit](api):


  inline def apply(inline pf: PartialFunction[Any,Unit]): Unit =
    ${  
      Select.foreverImpl('pf,'api)
    }

  def runAsync(): F[Unit] = async[F]{
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

  inline def run(): Unit = await(runAsync())







  






