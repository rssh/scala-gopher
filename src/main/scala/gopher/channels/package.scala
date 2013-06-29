package gopher

import scala.reflect._
import scala.concurrent._

package object channels {

  def makeChannel[A: ClassTag](capacity:  Int = 1000)(implicit ec: ExecutionContext): InputOutputChannel[A] = 
    {
      val retval = new GBlockedQueue[A](capacity,ec);
      //retval.process(executionContext);
      retval;
    }
  
  
  
}