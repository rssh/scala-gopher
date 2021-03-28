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
  

  test("select error handling for foreach")  {
     val channel = makeChannel[Int](100)

     var svEx: Throwable = null

   
     var nWrites = 0
     var nErrors = 0
   
     //implicit val printCode = cps.macroFlags.PrintCode
     //implicit val debugLevel = cps.macroFlags.DebugLevel(10)


     val g = async{
      try {
         
        select.loop{
           case x: channel.write if x == nWrites => 
             nWrites = nWrites + 1
             if (nWrites == 50) then
                throw new RuntimeException("Be-be-be")
             (nWrites != 100)
          case t: Time.after if t == (100 milliseconds) =>
             false
        }
      } catch {
          case ex: RuntimeException =>
            svEx = ex
        } 
     }

     
     async {
       val tf = channel.atake(50)
       await(g)
       assert(svEx.getMessage == "Be-be-be")
     }
     

  }


  test("select error handling for once")  {
    val channel = makeChannel[Int](100)

    var svEx: Throwable = null
    

    val g = async {
      try {
          select.once {
            case x: channel.write if (x==1) =>
              throw new RuntimeException("Be-be-be")
              2
            //case ex: select.error =>
            //{ };  svEx = ex  // macro-system errors: assignments accepts as default argument
            //    3
          }
      } catch {
        case ex: RuntimeException =>
          svEx = ex
          3
      }
    }

    async {
        val r = await(g)
        assert(svEx.getMessage == "Be-be-be")
        assert(r == 3)
    }

  }

  
  test("select error handling for input")  {
    val channel = makeChannel[Int](100)

    var svEx: Throwable = null
    
    async {

      val out = select.map { s =>
        var wasError = false
        s.apply{
          case x: channel.read =>
             try {
              if (x==55) {
                 throw new RuntimeException("Be-be-be")
              }
             } catch {
               case ex: RuntimeException =>
                wasError = true
                svEx = ex
             }
      //case ex: select.error =>
      //       {}; svEx = ex
      //       56
              if (wasError) then
                56
              else
                x
        }
      }
    
      channel.awriteAll(1 to 100)

      val g = out.atake(80)

      val r = await(g)

      assert(svEx.getMessage == "Be-be-be")

      assert(r.filter(_ == 56).size == 2)

    }

  }

  
  test("select error handling for fold") {
    val ch1 = makeChannel[Int]()
    val ch2 = makeChannel[Int]()
    val ch3 = makeChannel[Int]()

    var svEx: Throwable = null

    val g = 
      select.afold((ch1,ch2,0,List[Int]())) { case (x,y,z,l) =>
        try {
          select{
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
          }
        }catch{
          case ex: RuntimeException =>
            svEx = ex
            if (z > 20) {
               SelectFold.Done((x,y,z,z::l))
            } else {
              (x,y,z,l)
            }
        }
    }

    ch3.awriteAll(1 to 11).flatMap{
      x => ch2.awriteAll(1 to 5)
    }

    async {

      val r =await(g)

      assert(svEx.getMessage=="Be-be-be-1")

    }

  }


}
