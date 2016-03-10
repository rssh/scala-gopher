package gopher

import gopher.channels._
import scala.concurrent.{Channel=>_,_}
import scala.concurrent.duration._
import scala.language.experimental.macros
import scala.language.postfixOps
import scala.reflect.macros.blackbox.Context
import scala.util._
import java.util.concurrent.atomic.AtomicLong

class GopherAPI
{


  def makeChannel[A](capacity: Int = 0): Channel[A] = ???

  /**
   * execution context used for managing calculation steps in channels engine.
   **/
  def executionContext: ExecutionContext = ???

  private[gopher] def continue[A](next:Future[Continuated[A]], ft:FlowTermination[A]): Unit = ???
 

  
}

object GopherAPI
{


}
