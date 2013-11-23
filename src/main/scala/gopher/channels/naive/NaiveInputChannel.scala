package gopher.channels.naive

import gopher.channels._
import scala.concurrent._

trait NaiveInputChannel[+A] extends InputChannelBase[A] 
                                 with InputChannelOps[NaiveChannelsAPI,A]
                                 with Activable {
 
  
  def addReadListener(tie: NaiveTie,f: A => Boolean): Unit =
    addReadListener(tie,new ReadAction[A] {
      override def apply(input:ReadActionInput[A]): Option[Future[ReadActionOutput]] =
        Some(Promise successful ReadActionOutput(true) future)
    } )
  
  /**
   * Add listener which notified when new element is available
   * when it is added to queue. 
   */
  def addReadListener(tie: NaiveTie,f: ReadAction[A] ): Unit

  
  
}