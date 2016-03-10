package gopher.channels


import scala.concurrent._
import scala.concurrent.duration._
import gopher._
import scala.language.experimental.macros
import scala.language.postfixOps
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._

abstract class Channel[A](override val api: GopherAPI) extends InputOutput[A]
{


   def close(): Unit

}

