package gopher.channels


import org.scalatest._
import scala.concurrent._
import scala.concurrent.duration._
import gopher._


class FlowTerminationSuite extends FunSuite 
{

 
   test("flowTermination covariance assignment")  {
     
     val fUnit = PromiseFlowTermination[Unit]()
      // val fAny: FlowTermination[Any] = fUnit
     implicit val f_ : FlowTermination[_] = fUnit
     
     val qq = implicitly[FlowTermination[_]]
     
   }

   
/*
  
   test("select with queue type") {

     val channel = make[Int](100)

     val producer = Future {
       for( i <- 1 to 1000) {
         channel <~ i 
       }       
     }
          
     var sum = 0;
     val consumer = Future {
       val sc = new SelectorContext()
       sc.addInputAction(channel, 
            (i: channel.OutputElement) => { sum = sum + i; 
                          if (i == 1000) {
                            sc.shutdown()
                          }
                          Promise successful true future 
                        }
       )
       Await.ready(sc.go, 1000.second)
     }
   
    
     Await.ready(consumer, 1000.second)
     
   }
    
  */ 
  
  def gopherApi = CommonTestObjects.gopherApi
   
}
