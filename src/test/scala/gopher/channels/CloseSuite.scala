package gopher.channels


import org.scalatest._
import scala.concurrent._
import scala.concurrent.duration._
import gopher._
import gopher.tags._


import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global

class CloseSuite extends FunSuite 
{

 
   test("writing after close is impossile")  {
     
     val channel = gopherApi.makeChannel[Int](100)
     
     channel.close

     val producer = channel.awriteAll(1 to 1000)
       
     Await.ready(producer, 10.second)

     assert(producer.isCompleted)
     assert(producer.value.get.isFailure)
   }

   test("in async we must see throw")  {

     val channel = gopherApi.makeChannel[Int](100)
     channel.close
     @volatile var catched = false
     @volatile var notCatched = false
     val p = async {
       channel.write(1)
       notCatched=true
     }
     try {
        Await.result(p, 10.second)
     } catch {
       case ex: ChannelClosedException => 
         catched = true
     }
     assert(!notCatched) 
     assert(catched) 

   }

   test("after close we can read but not more, than was send", Now)  {
     val channel = gopherApi.makeChannel[Int](100)
     @volatile var q = 0
     val p = async {
       channel <~ 1
       channel.close
       q = channel.read
     }
     Await.result(p, 10.second)
     assert(q==1)
     val afterClose = async{
            val a = channel.read
            q = 2
     }
     Await.ready(afterClose, 10.second)
     assert(q != 2)
   }

  def gopherApi = CommonTestObjects.gopherApi
   
}
