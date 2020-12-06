package gopher.impl

import gopher._

/**
* Buffer.  access to buffer is exclusive by owner channel, 
* different loops can start in different threads but only one loop can be active at the samw time
**/
trait SPSCBuffer[A] {

   def isEmpty(): Boolean
  
   def startRead(): A
   def finishRead(): Boolean
  
   def isFull(): Boolean
   // prcondition: !isFull()
   def write(a: A): Boolean 


   // set local state from published
   def local(): Unit 

   // make buffer be readable from other thread than
   def publish(): Unit
   
}