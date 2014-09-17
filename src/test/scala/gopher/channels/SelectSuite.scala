package gopher.channels


import org.scalatest._
import scala.concurrent._
import scala.concurrent.duration._
import gopher._
import gopher.tags._


class SelectSuite extends FunSuite 
{

 
   test("basic select with reading syntax sugar")  {
     
     val channel = gopherApi.makeChannel[Int](100)
     
     val producer = channel.awriteAll(1 to 1000)
       
     @volatile var sum = 0;
     val consumer = gopherApi.select.forever.reading(channel).withFlowTermination { (i, ft) =>
                                       sum = sum+i
                                       if (i==1000) ft.doExit(())
                                     }.go


     
     Await.ready(consumer, 1000.second)

    // System.err.println("sum="+sum);
     
     val xsum = (1 to 1000).sum
    // System.err.println("xsum="+xsum);
     assert(xsum == sum)
   }

   test("basic select with 'apply' reading syntax sugar")  {

     val channel = gopherApi.makeChannel[Int](100)
     val producer = channel.awriteAll(1 to 1000)

     @volatile var sum = 0;
     val consumer = gopherApi.select.forever.reading(channel) { i =>
                                       sum = sum+i
                                       if (i==1000) gopherApi.currentFlow.exit(())
                                     }.go

     Await.ready(consumer, 1000.second)
     val xsum = (1 to 1000).sum
     assert(xsum == sum)

   }

    
   test("basic select with async reading form oter stream in apply")  {

     val channel1 = gopherApi.makeChannel[Int](100)
     val channel2 = gopherApi.makeChannel[Int](100)

     val producer1 = channel1.awriteAll(1 to 1000)
     val producer2_1 = channel2.awriteAll(1 to 10)

     @volatile var sum = 0;
     val consumer = gopherApi.select.forever.reading(channel1) { i1 =>
                                       val i2 = channel2.read
                                       sum = sum+i1 + i2
                                       if (i1==1000) gopherApi.currentFlow.exit(())
                                     }.go

     assert(consumer.isCompleted == false, "consumer must not be complete after reading first stream" )
     assert(producer1.isCompleted == false)

     val producer2_2 = channel2.awriteAll(1 to 1000)

     Await.ready(consumer, 1000.second)

     assert(consumer.isCompleted)

   }

   test("basic select write with apply")  {

     val channel = gopherApi.makeChannel[Int](1)

     @volatile var x = 1
     @volatile var y = 1
     val producer = gopherApi.select.forever.writing(channel,x) { 
                      var z = x + y
                      x=y
                      y=z    
                      if (z > 1000) {
                        channel.close()
                        gopherApi.currentFlow.exit(())
                      }
                    }.go

     @volatile var last = 0
     channel.foreach{ i=>
        //System.out.printn(i)
        last=i
     }

     Await.ready(producer, 1000.second)
     
     assert(producer.isCompleted)
     //assert(consumer.isCompleted)
     assert(last!=0)

   }

   test("basic select idlle with apply")  {

     @volatile var x = 0
     val selector = gopherApi.select.forever.idle{ 
                      if (x >= 10) {
                         gopherApi.currentFlow.exit(())
                      } 
                      x=x+1
                    }.go


     Await.ready(selector, 10.second)
     assert(selector.isCompleted)
     assert(x==11)

   }
   
   test("basic compound select with apply", Now)  {

     import scala.concurrent.ExecutionContext.Implicits.global

     val channel1 = gopherApi.makeChannel[Int](1)
     val channel2 = gopherApi.makeChannel[Int](1)
     val channel3 = gopherApi.makeChannel[Int](1)
     val channel4 = gopherApi.makeChannel[Int](1)

     val producer = channel1.awriteAll(1 to 1000)

     @volatile var x=0
     @volatile var nw=0
     @volatile var q = false
     @volatile var ch1s=0
 
     val selector = gopherApi.select.forever.reading(channel1) { i =>
                                   // read ch1 in selector
                                   channel4.awrite(i)
                                   ch1s=i           
                                 }.reading(channel2) { i =>
                                  {}; // workarround for https://issues.scala-lang.org/browse/SI-8846
                                  x=i
                                  //Console.println(s"reading from ch2, i=${i}")
                                }.writing(channel3,x) {
                                  {};
                                  nw=nw+1        
                                  //Console.println(s"writing ${x} to ch3, nw=${nw}")
                                }.idle {
                                  //Console.println(s"idle, exiting")
                                  {};
                                  q=true
                                  gopherApi.currentFlow.exit(())
                                }.go

     for(c <- channel4) channel2.write(c)

     Await.ready(selector, 10.second)
     assert(selector.isCompleted)
     assert(q==true)

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
