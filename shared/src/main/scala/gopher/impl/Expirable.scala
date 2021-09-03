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
    * capture object, and after this we can or use one (markUsed will be called) or abandon (markFree)
    **/
   def capture(): Expirable.Capture[A]

   /**
    * Called when we submitt to task executor readFunction and now is safe to make exprire all other readers/writers in the 
    * same select group
    **/
   def markUsed(): Unit

   /**
    * Called when  we can't use captured function (i.e. get function but ).
    **/
   def markFree(): Unit



object Expirable:

   enum Capture[+A]:   
      case Ready(value: A)
      case WaitChangeComplete
      case Expired

      def map[B](f: A=>B): Capture[B] =
         this match
            case Ready(a) => Ready(f(a))
            case WaitChangeComplete => WaitChangeComplete
            case Expired => Expired

      def foreach(f: A=>Unit): Unit =
         this match
            case Ready(a) => f(a)
            case _  =>