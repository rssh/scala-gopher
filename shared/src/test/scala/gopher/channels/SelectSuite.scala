package gopher.channels


import munit._
import scala.language._
import scala.concurrent._
import scala.concurrent.duration._

import cps._
import gopher._
import cps.monads.FutureAsyncMonad


class SelectSuite extends FunSuite 
{

  import scala.concurrent.ExecutionContext.Implicits.global
  given Gopher[Future] = SharedGopherAPI.apply[Future]()


 
   test("basic select with reading syntax sugar")  {
     
    val channel = makeChannel[Int](100)
     
    val producer = channel.awriteAll(1 to 1000)
     
    async {

      @volatile var sum = 0;
      val consumer = select.loop.reading(channel){ i =>
                                       sum = sum+i
                                       i < 1000
      }.runAsync()
      await(consumer)

      val xsum = (1 to 1000).sum   
      assert(xsum == sum)
    }
   }


    
   test("basic select with async reading form oter stream in apply")  {

    async{
      val channel1 = makeChannel[Int](100)
      val channel2 = makeChannel[Int](100)

      val producer1 = channel1.awriteAll(1 to 1000)
      val producer2_1 = channel2.awriteAll(1 to 10)


      @volatile var sum = 0;
      // but when reading instead onRead
      // TODO: submit bug to doty
      val consumer = select.loop.onRead(channel1) { i1 =>
                                       val i2 = channel2.read
                                       sum = sum+i1 + i2
                                       (i1 < 1000) 
                                      } .runAsync()

      assert(consumer.isCompleted == false, "consumer must not be complete after reading first stream" )
      assert(producer1.isCompleted == false)

      val producer2_2 = channel2.awriteAll(1 to 1000)

      await(consumer)

      assert(consumer.isCompleted)
    }

   }

   /*

   test("basic select write with apply")  {

     val channel = gopherApi.makeChannel[Int](1)

     @volatile var x = 1
     @volatile var y = 1
     val producer = gopherApi.select.forever.writing(channel,x) { _ =>
                      var z = x + y
                      x=y
                      y=z    
                      if (z > 1000) {
                        channel.close()
                        gopherApi.currentFlow.exit(())
                      }
                    }.go

     @volatile var last = 0
     channel.aforeach{ i=>
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
                      } else { 
                         x=x+1
                      }
                    }.go


     Await.ready(selector, 10.second)
     assert(selector.isCompleted)
     assert(x==10)

   }
   
   test("basic compound select with apply")  {

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
                                }.writing(channel3,x) { x =>
                                  {}; // workarround for https://issues.scala-lang.org/browse/SI-8846
                                  nw=nw+1        
                                  //Console.println(s"writing ${x} to ch3, nw=${nw}")
                                }.idle {
                                  //Console.println(s"idle, exiting")
                                  {};
                                  q=true
                                  gopherApi.currentFlow.exit(())
                                }.go

     for(c <- channel4.async) channel2.write(c)

     Await.ready(selector, 10.second)
     assert(selector.isCompleted)
     assert(q==true)

   }


   test("basic compound select with for syntax")  {
    
     import scala.concurrent.ExecutionContext.Implicits.global
     import scala.async.Async._

     val channel1 = gopherApi.makeChannel[Int](1)
     val channel2 = gopherApi.makeChannel[Int](1)
     val channel3 = gopherApi.makeChannel[Int](1)
     val channel4 = gopherApi.makeChannel[Int](1)

     val producer = channel1.awriteAll(1 to 1000)

     @volatile var q = false

     val selector = async {
       @volatile var x=0
       @volatile var nw=0
       @volatile var ch1s=0
 
       //pending
       // for syntax will be next:
       for(s <- gopherApi.select.forever)  
        s match {
         case ir: channel1.read  =>
                                   channel4.awrite(ir)
                                   ch1s=ir           
         case iw: channel3.write if (iw==(x+1)) =>
                                   {}; nw = nw+1
         case _    => {}; q=true
                      implicitly[FlowTermination[Unit]].doExit(())
       }

     }

     for(c <- channel4.async) channel2.write(c)

     Await.ready(selector, 10.second)
     assert(selector.isCompleted)
     assert(q==true)

   }


   test("basic select.once with reading syntax sugar")  {

     val channel1 = gopherApi.makeChannel[String](1)
     val channel2 = gopherApi.makeChannel[String](1)
     val selector = (gopherApi.select.once.reading(channel1)(x=>x)
                                                  .reading(channel2)(x=>x)
                    ).go
     channel2.awrite("A")
     assert(Await.result(selector, 10.second)=="A")
     
   }

   test("basic select.once with writing syntax sugar")  {
     val channel1 = gopherApi.makeChannel[Int](100)
     val channel2 = gopherApi.makeChannel[Int](100)
     @volatile var s:Int = 0
     val selector = (gopherApi.select.once.writing(channel1,s){q:Int =>"A"}
                                          .writing(channel2,s){s=>"B"}
                    ).go
     // hi, Captain Obvious
     assert(Set("A","B") contains Await.result(selector, 10.second) )
     channel1.close()
     channel2.close()
   }

   test("basic select.once with idle syntax sugar")  {
     val ch = gopherApi.makeChannel[String](1)
     val selector = (gopherApi.select.once[String].reading(ch)(x=>x)
                                                  .idle("IDLE")
                    ).go
     assert(Await.result(selector, 10.second)=="IDLE")
     ch.close()
   }


   test("basic select.foreach with partial-function syntax sugar")  {
     val info = gopherApi.makeChannel[Long](1)
     val quit = gopherApi.makeChannel[Int](2)
     @volatile var (x,y)=(0L,1L)
     val writer = gopherApi.select.forever{
                      case z:info.write if (z==x) =>
                                              x = y
                                              y = y + x
                      case q:quit.read =>
                                         implicitly[FlowTermination[Unit]].doExit(())
                  }
     @volatile var sum=0L
     val reader = gopherApi.select.forever{
                      case z:info.read => sum += z
                                          if (sum > 100000) {
                                            quit.write(1)
                                            implicitly[FlowTermination[Unit]].doExit(())
                                         }
                  }
     Await.ready(writer, 10.second)
     Await.ready(reader, 10.second)
     assert(sum > 100000)
   }
  
  */
   
}
