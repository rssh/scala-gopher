package gopher

import scala.util.Try
import cps._

trait Reader[A] extends Expirable[Try[A]=>Unit]

