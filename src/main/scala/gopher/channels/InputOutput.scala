package gopher.channels

import gopher._

trait InputOutput[A] extends Input[A] with Output[A]
