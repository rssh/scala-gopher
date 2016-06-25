package gopher.channels

import gopher._
import gopher.channels._
import gopher.tags._

import org.scalatest._

import scala.language._
import scala.concurrent._
import scala.concurrent.duration._

import akka.util.Timeout

class SelectTimeoutSuite extends FunSuite 
{

   import scala.concurrent.ExecutionContext.Implicits.global

  
   test("select with constant timeout which not fire")  {
     //pending
     import gopherApi._
     val ch1 = makeChannel[Int](10)
     val r = select.amap {
       case x:ch1.read =>
                  //System.err.println(s"readed ${x}")
                  x
       case y:select.timeout if (y==500.milliseconds) =>
                 //System.err.println(s"timeout ${y}")
                 -1
     }
     val f1 = ch1.awrite(1)
     val x = Await.result(r.aread, 10 seconds)
     assert(x==1)
   }

   test("select with constant timeout which fire")  {
     import gopherApi._
     val ch1 = makeChannel[Int](10)
     val r = select.amap {
       case x:ch1.read =>
                  //System.err.println(s"readed ${x}")
                  x
       case x:select.timeout if (x==500.milliseconds) =>
                 //System.err.println(s"timeout ${x}")
                 -1
     }
     val x = Await.result(r.aread, 10 seconds)
     assert(x == -1)
   }

   test("timeout in select.forever")  {
     import gopherApi._
     val ch1 = makeChannel[Int](10)
     val ch2 = makeChannel[Int]()
     val chS = makeChannel[String](10)
     var s = 0
     implicit val timeout = Timeout(100 milliseconds)
     val f = select.forever{
               case x: ch1.read => 
                             chS.write("1") 
               case x: ch2.read => 
                             chS.write("2") 
               case x:select.timeout =>
                             s += 1
                             chS.write("t") 
                             if (s > 2) select.exit(())
             }
     val x = Await.result(f, 10 seconds)
     assert(s > 2)
   }


   lazy val gopherApi = CommonTestObjects.gopherApi
   
}
