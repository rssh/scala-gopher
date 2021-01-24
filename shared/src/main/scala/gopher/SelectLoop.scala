package gopher

import cps._
import scala.quoted._
import scala.compiletime._
import scala.concurrent.duration._

class SelectLoop[F[_]:CpsSchedulingMonad](api: Gopher[F]) extends SelectGroupBuilder[F,Boolean](api):


  inline def apply(inline pf: PartialFunction[Any,Boolean]): Unit =
    ${  
      Select.loopImpl[F]('pf,  'api )  
    }
      
  def runAsync(): F[Unit] = async[F] {
      while{
        val group = api.select.group[Boolean]
        val r = groupBuilder(group).run()
        r
      } do ()
  }

  inline def run(): Unit = await(runAsync())

   
  


