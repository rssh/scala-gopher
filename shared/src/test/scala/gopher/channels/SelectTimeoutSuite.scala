package gopher.channels

import cps._
import gopher._
import munit._

import scala.language.postfixOps
import scala.concurrent._
import scala.concurrent.duration._

import cps.monads.FutureAsyncMonad

class SelectTimeoutSuite extends FunSuite 
{

   import scala.concurrent.ExecutionContext.Implicits.global

   given Gopher[Future] = SharedGopherAPI.apply[Future]()

  
   test("select with constant timeout which not fire")  {
     async {
      val ch1 = makeChannel[Int](10)
      val r = select.map{ s =>
         s.apply{
           case x:ch1.read =>
                  //System.err.println(s"readed ${x}")
                  x
           case y:  Time.after if (y==500.milliseconds) =>
                 //System.err.println(s"timeout ${y}")
                 -1
         }
      }
      val f1 = ch1.awrite(1)
      val x =  r.read()
      assert(x==1)
     }
   }

   
   test("select with constant timeout which fire")  {
     async {
      val ch1 = makeChannel[Int](10)
      val r = select.map{ s =>
        s.apply{
          case x:ch1.read =>
                  //System.err.println(s"readed ${x}")
                  x
          case x:Time.after if (x==500.milliseconds) =>
                 //System.err.println(s"timeout ${x}")
                 -1
        }
      }
      val x = r.read()
      assert(x == -1)
     }
   }

   
   test("timeout in select.loop")  {
     async {
      val ch1 = makeChannel[Int](10)
      val ch2 = makeChannel[Int]()
      val chS = makeChannel[String](10)
      var s = 0
      select.loop{ 
                  case x: ch1.read => 
                             chS.write("1") 
                             true
                  case x: ch2.read => 
                             chS.write("2")
                             true 
                  case x: Time.after if x == (100 millis) =>
                             s += 1
                             chS.write("t") 
                             (! (s > 2) ) 
      }
      assert(s > 2)
     }
   }

   
   test("timeout in select.fold")  {
        val ch1 = makeChannel[Int](10)
        val f = async {
                 select.fold(0) { state => 
                    select{
                      case x: ch1.read => state+1
                      case x: Time.after if (x == 100.milliseconds)  => 
                                                     SelectFold.Done((state+10))
                    }
                }
        } 
        ch1.awrite(1)
        async {
          val x = await(f)
          assert(x==11)
        } 
   }

   
   test("timeout in select.once")  {
     val ch1 = makeChannel[Int](10)
     var x = 0
     async {
       select.once{
            case y: ch1.read => //println("ch1 readed")
                                x=1
            case y: Time.after if y == (100 milliseconds) => 
                                //println("ch2 readed")
                                x=10 
       }
       assert(x==10) 
     }
   }

   
}
