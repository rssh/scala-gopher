package go

import scala.reflect._

package object channels {

  def makeChannel[A: ClassTag](capacity:  Int = 1000): InputOutputChannel[A] = new GBlockedQueue[A](capacity)
  
}