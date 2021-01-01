package gopher.channels


import munit._
import scala.language.postfixOps
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

   

   test("basic select write with apply")  {

    val channel = makeChannel[Int](1)

    async {
      @volatile var x = 1
      @volatile var y = 1
      val producer = select.loop.writing(channel,x) { _ =>
                      var z = x + y
                      x=y
                      y=z    
                      if (z > 1000) {
                        channel.close()
                        false
                      } else {
                        true
                      }
                    }.runAsync()

      @volatile var last = 0
      channel.foreach{ i=>
        //System.out.printn(i)
        last=i
      }

      assert(last!=0)
      await(producer) // should be completed shortly
 
    }

   }

  
   test("basic select timeout (was idle in 0.99) with apply")  {

     @volatile var x = 0
     val selector = select.loop.onTimeout(5 millisecond){ dt =>
                      x = x + 1
                      x < 10
                    }.runAsync()

     async {
       await(selector)
       assert(x == 10)
     }               
     
   }
  
  
   test("basic compound select with apply")  {

    async {
      val channel1 = makeChannel[Int](1)
      val channel2 = makeChannel[Int](1)
      val channel3 = makeChannel[Int](1)
      val channel4 = makeChannel[Int](1)

      val producer = channel1.awriteAll(1 to 1000)

      @volatile var x=0
      @volatile var nw=0
      @volatile var q = false
      @volatile var ch1s=0

      val selector = select.loop.reading(channel1) { i =>
                                   // read ch1 in selector
                                   channel4.awrite(i)
                                   ch1s=i   
                                   true        
                                 }.reading(channel2) { i =>
                                  //{}; // workarround for https://issues.scala-lang.org/browse/SI-8846
                                  x=i
                                  //Console.println(s"reading from ch2, i=${i}")
                                  true
                                }.writing(channel3,x) { x =>
                                  //{}; // workarround for https://issues.scala-lang.org/browse/SI-8846
                                  nw=nw+1
                                  //Console.println(s"writing ${x} to ch3, nw=${nw}")
                                  true        
                                }.onTimeout(5 milliseconds) { dt =>
                                  //Console.println(s"idle, exiting")
                                  //{};
                                  channel4.close()
                                  q=true
                                  false
                                }.runAsync()


      for(c <- channel4) 
        channel2.write(c)

      await(selector)
      assert(q==true)

    }
  }



  test("basic compound select with loop select syntax")  {
    

      val channel1 = makeChannel[Int](1)
      val channel2 = makeChannel[Int](1)
      val channel3 = makeChannel[Int](1)
      val channel4 = makeChannel[Int](1)

      val producer = channel1.awriteAll(1 to 1000)

      @volatile var q = false

      val selector = async {
       @volatile var x=0
       @volatile var nw=0
       @volatile var ch1s=0
 
       //pending
       // for syntax will be next:
       select.loop{ 
           case ir: channel1.read  =>
                                   channel4.awrite(ir)
                                   ch1s=ir      
                                   true     
           case iw: channel3.write if (iw==(x+1)) =>
                                   {}; 
                                   nw = nw+1
                                   true
           case t: Time.after if t == (5 milliseconds) => q=true
                                  false
       }

      }

      val copier =   async{
          for(c <- channel4) channel2.write(c)
      }
     
      async{
        await(selector)

        assert(q==true)
      }

  }
  


   test("basic select.group with reading syntax sugar")  {

    async {
      val channel1 = makeChannel[String](1)
      val channel2 = makeChannel[String](1)
      val selector = select.group[String].onRead(channel1)(x=>x)
                               .onRead(channel2)(x=>x)
                              .runAsync()
      channel2.awrite("A")
      val r = await(selector)
      assert(r=="A")
     
    }
   }

   
   test("basic select.once with writing syntax sugar")  {
     async {
      val channel1 = makeChannel[Int](100)
      val channel2 = makeChannel[Int](100)
      @volatile var s:Int = 0
      val selector = (select.group.onWrite(channel1,s){ (q:Int) =>"A"}
                                  .onWrite(channel2,s){s=>"B"}
                    ).runAsync()
      //println("before awaiting selector")
      val r = await(selector)
      //println("after awaiting selector")
      
      // hi, Captain Obvious
      assert(Set("A","B") contains r )
      channel1.close()
      channel2.close()
    }
   }
   

  
   test("basic select.once with idle syntax sugar")  {
     async{
      val ch = makeChannel[String](1)
      val selector = (select.once[String].onRead(ch)(x=>x)
                                        .onTimeout(5 milliseconds)(t => "IDLE")
                    ).runAsync()
      val r = await(selector)              
      assert(r=="IDLE")
      ch.close()
     }
   }


   
   test("basic select.foreach with partial-function syntax sugar")  {
      val info = makeChannel[Long](1)
      val quit = makeChannel[Int](2)
      @volatile var (x,y)=(0L,1L)
      
      val writer = async {
        select.loop{
                      case z:info.write if (z==x) =>
                                              x = y
                                              y = y + x
                                              true
                      case q:quit.read =>
                                        false
        }
      }

      @volatile var sum=0L
      val reader = {
      //implicit val printCode = cps.macroFlags.PrintCode
      //implicit val debugLevel = cps.macroFlags.DebugLevel(20)
        async{
          select.loop{
                      case z:info.read => sum += z
                                          if (sum > 100000) {
                                            //quit.write(1)
                                            await(quit.awrite(1))
                                            false
                                          } else {
                                            true
                                          }
                  }
        }
      }

      async{
        await(writer)
        await(reader)
        assert(sum > 100000)
      }
   }
  

   
}
