package gopher.channels

import cps._
import gopher._
import munit._

import scala.language.postfixOps
import scala.concurrent._
import scala.concurrent.duration._

import cps.monads.FutureAsyncMonad


class SelectErrorSuite extends FunSuite
{

   import scala.concurrent.ExecutionContext.Implicits.global
   given Gopher[Future] = SharedGopherAPI.apply[Future]()
  
/*
   test("select error handling for foreach")  {
     val channel = makeChannel[Int](100)

     var svEx: Throwable = null

   
     var nWrites = 0
     var nErrors = 0
   
     //implicit val printCode = cps.macroFlags.PrintCode
     //implicit val debugLevel = cps.macroFlags.DebugLevel(10)


     async{
       //try {
         
        select.loop{
           case x: channel.write if x == nWrites => 
             nWrites = nWrites + 1
             if (nWrites == 50) then
                throw new RuntimeException("Be-be-be")
             (nWrites != 100)
          // case t: Time.after if t == (100 milliseconds) =>
          //   false
        }
       //} catch {
       //   case ex: RuntimeException =>
       //     svEx = ex
       //} 
       
     }

     /*
     async {
       val tf = channel.atake(50)
       await(g)
       assert(svEx.getMessage == "Be-be-be")
     }
     */

   }

*/   

/*
  test("select error handling for once")  {
    import gopherApi._
    val channel = makeChannel[Int](100)

    var svEx: Throwable = null
    val x = 1

    val g = go {
      for (s <- select.once) {
        s match {
          case x: channel.write  =>
              throw new RuntimeException("Be-be-be")
          case ex: select.error =>
          { };  svEx = ex  // macro-system errors: assignments accepts as default argument
                3
        }
      }
    }

    val r = Await.result(g, 10 seconds)

    assert(svEx.getMessage == "Be-be-be")

    assert(r === 3)


  }

  test("select error handling for input")  {
    import gopherApi._
    val channel = makeChannel[Int](100)

    var svEx: Throwable = null

    val out = select.map {
      case x: channel.read =>
             if (x==55) {
               throw new RuntimeException("Be-be-be")
             }
             x
      case ex: select.error =>
             {}; svEx = ex
             56
    }

    channel.awriteAll(1 to 100)

    val g = out.atake(80)

    val r = Await.result(g, 10 seconds)

    assert(svEx.getMessage == "Be-be-be")

    assert(r.filter(_ == 56).size == 2)

  }

  test("select error handling for fold") {
    import gopherApi._
    val ch1 = makeChannel[Int]()
    val ch2 = makeChannel[Int]()
    val ch3 = makeChannel[Int]()

    var svEx: Throwable = null

    val g = select.afold((ch1,ch2,0,List[Int]())) { case ((x,y,z,l),s) =>
        s match {
          case z1: ch3.read =>
               if (z1==10) {
                 throw new RuntimeException("Be-be-be!")
               }
               (x,y,z1,z1::l)
          case a:x.read =>
               if (z > 20) {
                 throw new RuntimeException("Be-be-be-1")
               }
               (y,x,z+a,z::l)
          case b:y.read =>
               (y,x,z+100*b,z::l)
          case ex: select.error =>
             {}; svEx = ex
               if (z > 20) {
                 select.exit((x,y,z,z::l))
               } else
                (x,y,z,l)

        }
    }

    ch3.awriteAll(1 to 11).flatMap{
      x => ch2.awriteAll(1 to 5)
    }

    val r = Await.result(g, 5 seconds)

    assert(svEx.getMessage=="Be-be-be-1")


   // System.err.println(s"received: ${r._4.reverse}")

  }





    lazy val gopherApi = CommonTestObjects.gopherApi
    */
   
}
