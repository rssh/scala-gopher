package gopher.channels

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import gopher._
import scala.concurrent._
import scala.annotation.unchecked._

trait SelectorBuilder[A]
{

   def api: GopherAPI

}

