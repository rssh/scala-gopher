package gopher.channels

import gopher._
import gopher.channels._
import gopher.tags._

import org.scalatest._

import scala.concurrent._
import scala.concurrent.duration._

class MacroSelectSuite extends FunSuite 
{

   import scala.concurrent.ExecutionContext.Implicits.global

  
   test("select emulation with macroses")  {
     
     val channel = gopherApi.makeChannel[Int](100)
     
     go {
       var i = 1
       while(i <= 1000) {
         channel <~ i 
         i+=1
       }
       //TODO: implement for in goas preprocessor to async
       //for( i <- 1 to 1000) 
       //  channel <~ i 
     }
     
     var sum = 0;
     val consumer = go {
       for(s <- gopherApi.select.forever) {
          s match {
             case i: channel.read =>
                     //System.err.println("received:"+i)
                     sum = sum + i
                     if (i==1000)  
                        implicitly[FlowTermination[Unit]].doExit(())
          }
       }
       sum
     }

     Await.ready(consumer, 5.second)

     val xsum = (1 to 1000).sum
     assert(xsum == sum)
     
     
   }

   
   test("select with run-once")  {
     import gopherApi._
     val channel1 = makeChannel[Int](100)
     val channel2 = makeChannel[Int](100)

     val g = go {
      var nWrites=0;
      for(s <- select.once) 
        s match {
          case x: channel1.write if (x==1) => { {}; nWrites = nWrites + 1 }
          case x: channel2.write if (x==1) => { {}; nWrites = nWrites + 1 }
        }

      @volatile var nReads=0;
      for(s <- select.once) 
        s match {
          case  x: channel1.read => { {}; nReads = nReads + 1 }
          case  x: channel2.read => { {}; nReads = nReads + 1 }
        }

     }
 
     Await.ready(g, 10 seconds)

   }

   test("select from futureInput")  {
     import gopherApi._
     val channel = makeChannel[Int](100)
     val future = Future successful 10
     val fu = futureInput(future)
     var res = 0
     val r = select.forever{
                case x: channel.read => 
                                     Console.println(s"readed from channel: ${x}")
                case x: fu.read => 
                                     //Console.println(s"readed from future: ${x}")
                                     res=x
                                     implicitly[FlowTermination[Unit]].doExit(())
                //  syntax for using channels/futures in cases without
                //  setting one in stable identifers.
                case x: Int if (x==future.read) =>
                                     {};
                                     res=x
     }
     Await.ready(r, 10 seconds)
     assert(res==10)
   }

   test("select syntax with read/writes in guard")  {
     import gopherApi._
     val channel1 = makeChannel[Int](100)
     val channel2 = makeChannel[Int](100)
     var res = 0
     val r = select.forever{
                case x: Int if (x==channel1.write(3)) => 
                                     Console.println(s"write to output")
                case x: Int if (x==channel2.read) => 
                                     Console.println(s"readed from channel2: ${x}")
                case x: Int if (x==(Future successful 10).read) => 
                                     res=x
                                     implicitly[FlowTermination[Unit]].doExit(())
     }
     Await.ready(r, 10 seconds)
     assert(res==10)
   }

   test("select syntax with @unchecked annotation")  {
     import gopherApi._
     val channel1 = makeChannel[List[Int]](100)
     val channel2 = makeChannel[List[Int]](100)
     var res = 0
     val r = select.once{
                case x: channel1.read @ unchecked => 
                              {};
                              res=1
                case x: List[Int] @ unchecked if (x==channel2.read) => 
                              {};
                              res=2
     }
     channel1.awrite(List(1,2,3))
     Await.ready(r, 10 seconds)
     assert(res==1)
   }

   test("tuple in caseDef as one symbol")  {
     import gopherApi._
     val ch = makeChannel[(Int,Int)](100)
     var res = 0
     val r = select.once{
                case xpair: ch.read @unchecked  => 
                              // fixed error in compiler: Can't find proxy
                              val (a,b)=xpair
                              res=1
     }
     ch.awrite((1,1))
     Await.ready(r, 10 seconds)
     assert(res==1)
   }

   test("multiple readers for one write")  {
     import gopherApi._
     val ch = makeChannel[Int](10)
     @volatile var x1 = 0
     @volatile var x2 = 0
     @volatile var x3 = 0
     @volatile var x4 = 0
     @volatile var x5 = 0
     val f1 = select.once{
                case x:ch.read =>
                            {};
                            x1=1
              }
     val f2 = select.once{
                case x:ch.read =>
                            {};
                            x2=1
              }
     val f3 = select.once{
                case x:ch.read =>
                            {};
                            x3=1
              }
     val f4 = select.once{
                case x:ch.read =>
                            {};
                            x4=1
             }
     val f5 = select.once{
                case x:ch.read =>
                            {};
                            x5=1
             }
     Await.ready(ch.awrite(1),1 second)
     val fr = Future.firstCompletedOf(List(f1,f2,f3,f4,f5)) 
     Await.ready(fr, 1 second)
     ch.close()
     Await.ready(Future.sequence(List(f1,f2,f3,f4,f5)),1 second)
     assert(x1+x2+x3+x4+x5==1)
   }

   lazy val gopherApi = CommonTestObjects.gopherApi
   
}
