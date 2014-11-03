package gopher

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



  def executionContext: ExecutionContext = implicitly[ExecutionContext]

  
}

