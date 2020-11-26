package gopher

import cps._

trait Expirable[A]:
 
   /**
    * called when reader/writer can become no more available for some reason
    */
   def canExpire: Boolean

   /**
    * if this object is expired and should be deleted from queue
    * (for example: when reader is belong to select group and some other action in this select group was performed)
    **/
   def isExpired: Boolean

   /**
    * capture object, and after this we can or return one
    **/
   def capture(): Option[A]

   /**
    * Called when we submitt to task executor readFunction and now is safe to make exprire all other readers/writers in the 
    * same select group
    **/
   def markUsed(): Unit

   /**
    * Called when it was a race condition and we can't use captured function.
    **/
   def markFree(): Unit

