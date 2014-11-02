package gopher.channels

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import gopher._
import gopher.util._
import scala.concurrent._
import scala.annotation.unchecked._

trait SelectorBuilder[A]
{

   def api: GopherAPI

   private[gopher] var selector=new Selector[A](api)

}

object SelectorBuilder
{


}

/**
 * Builder for 'forever' selector. Can be obtained as `gopherApi.select.forever`.
 **/
trait ForeverSelectorBuilder extends SelectorBuilder[Unit]
{

         

}


class SelectFactory(api: GopherAPI)
{
 
  selectFactory =>

  trait SelectFactoryApi
  {
    def api = selectFactory.api
  }

  /**
   * forever builder. 
   *@see ForeverSelectorBuilder
   */
  def forever: ForeverSelectorBuilder = new ForeverSelectorBuilder with SelectFactoryApi {}

}

