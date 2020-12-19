package gopher.impl

import cps._

/**
* Object, which can be expired
* (usually - reader or writer in SelectGroup)
* Usage protocol is next:
*  capture
*   if A inside is used, call markUsed and use A
*   if A inside is unused for some reason -- call markFree
**/
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

