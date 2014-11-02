package gopher

import gopher.channels._
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.util._
import java.util.concurrent.atomic.AtomicLong

import scala.concurrent.ExecutionContext.Implicits.global 

/**
 * Api for providing access to channel and selector interfaces.
 */
class GopherAPI()
{

  /**
   * obtain select factory
   *
   * {{{
   *  goopherApi.select.once[String] {
   *    case x: a.read => s"\${x} from A"
   *    case x: b.read => s"\${x} from B"
   *    case _ => "IDLE"
   *  }
   * }}}
   */
  def select: SelectFactory =
    new SelectFactory(this)


  def makeTransputer[T <: Transputer]: T = macro GopherAPI.makeTransputerImpl[T]

  def executionContext: ExecutionContext = implicitly[ExecutionContext]

  def currentFlow = CurrentFlowTermination

  
}

object GopherAPI
{

  def makeTransputerImpl[T <: Transputer : c.WeakTypeTag](c:Context):c.Expr[T] = {
    import c.universe._
    c.Expr[T](q"""{ def factory():${c.weakTypeOf[T]} = new ${c.weakTypeOf[T]} { 
                                                def api = ${c.prefix} 
                                                def recoverFactory = factory
                                     }
                    val retval = factory()
                    retval
                  }
               """)
  }

}
