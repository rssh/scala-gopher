package gopher

import cps._
import scala.quoted._
import scala.compiletime._
import scala.concurrent.duration._

class SelectLoop[F[_]](api: Gopher[F]) extends SelectGroupBuilder[F,Boolean, Unit](api):


  inline def apply(inline pf: PartialFunction[Any,Boolean]): Unit =
    ${  
      Select.loopImpl[F]('pf,  'api )  
    }
      
  def runAsync(): F[Unit] = 
    given CpsSchedulingMonad[F] = api.asyncMonad
    async[F]{
      while{
        val group = api.select.group[Boolean]
        val r = groupBuilder(group).run()
        r
      } do ()
    }


   
  


