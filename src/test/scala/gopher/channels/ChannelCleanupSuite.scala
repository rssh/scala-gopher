package gopher.channels


import org.scalatest._
import scala.concurrent._
import scala.concurrent.duration._
import gopher._
import gopher.tags._
import java.lang.ref._


import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global

object CleanupFlags
{
 @volatile var v1 = 0
}

class CleanedObject(val v: Int)
{
  override protected def finalize():Unit =
  {
   CleanupFlags.v1 = 1
   super.finalize()
  }
}

class ChannelCleanupSuite extends FunSuite 
{

 
   // This test is run, but JVM ntot guarantie this.
   //  so, it can 
   test("unused closed channel-actor must be cleanuped during gc")  {
     
     val cleanedObjectRq = new ReferenceQueue[CleanedObject]();
     var weakRef: WeakReference[CleanedObject] = null;

     def createChannel(): IOChannel[CleanedObject] =
     {
       val channel = gopherApi.makeChannel[CleanedObject](100)
       var obj = new CleanedObject(1)
       weakRef = new WeakReference(obj, cleanedObjectRq)
       val producer = channel.awrite(obj)
       obj = null
       channel
     }
       
     var ch = createChannel()
     ch = null;
     var quit=false;
     var nTryes = 0
     if (cleanedObjectRq.poll() == null) {
       while(!quit) {
         val x = (cleanedObjectRq.remove(100L) != null)
         // when we have not null, object in channel is garbage collected
         //  this can be never done, when we have enought memory, so
         //  look at finalizer 
         quit=(CleanupFlags.v1 == 1)
         System.gc();
         System.runFinalization()
         Thread.sleep(100)
         nTryes += 1
         //assert(nTryes < 100)
         if (nTryes >= 100) {
           cancel("Test to finalization is canceled, but it is not guarantued by JVM specs ")
         }
       }
     }

    // System.err.println("CleanupFlags.v1="+CleanupFlags.v1)

   }


  def gopherApi = CommonTestObjects.gopherApi
   
}
