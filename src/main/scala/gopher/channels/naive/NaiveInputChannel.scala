package gopher.channels.naive

import gopher.channels._

trait NaiveInputChannel[+A] extends InputChannel[A] {
 
  
  
  def addReadListener(tie: NaiveTie,f: A => Boolean): Unit =
    addReadListener(tie,new ReadAction[A] {
      def apply(input:ReadActionInput[A]): ReadActionOutput =
        ReadActionOutput(f(input.value),true)
    } )
  
  /**
   * Add listener which notified when new element is available
   * when it is added to queue. 
   */
  def addReadListener(tie: NaiveTie,f: ReadAction[A] ): Unit

  
  
}