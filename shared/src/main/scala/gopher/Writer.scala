package gopher

import scala.util.Try

trait Writer[A] extends Expirable[(A,Try[Unit]=>Unit)]

