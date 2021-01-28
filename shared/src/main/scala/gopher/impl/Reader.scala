package gopher.impl

import scala.util.Try

trait Reader[A] extends Expirable[Try[A]=>Unit] 

