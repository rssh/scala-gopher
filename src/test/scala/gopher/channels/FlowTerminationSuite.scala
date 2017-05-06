package gopher.channels


import gopher._
import org.scalatest._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.language.postfixOps

class FlowTerminationAsyncSuite extends FunSuite
{


  test("select with queue type") {
    import gopherApi._

    val channel = makeChannel[Int](100)

    val producer = channel.awriteAll(1 to 1000)

    var sum = 0;
    val consumer = {
      val sc = new Selector[Unit](gopherApi)
      def f(self: ContRead[Int,Unit]):Option[ContRead.In[Int]=>Future[Continuated[Unit]]] =
      {
        Some {
          case ContRead.Value(a) => sum = sum + a
            if (a == 1000) sc.doExit(())
            Future successful self
          case ContRead.Failure(e) => Future failed e
          case _     =>
            Future successful self
        }
      }
      sc.addReader(channel,f)
      sc.run
    }

    // main - that we finished
    consumer map (u => assert(true))

  }

  test("not propagate signals after exit") {

    import gopherApi._
    val channel = makeChannel[Int](100)
    var sum = 0
    for {u <- select.forever {
                case x: channel.read => sum += x
                     select.shutdown()
          }
         f2 <- channel.awrite(1)
         f3 <- channel.awrite(2)
    } yield assert (sum == 1)
  }

  val gopherApi = CommonTestObjects.gopherApi

}


class FlowTerminationSuite extends FunSuite 
{

 
   test("flowTermination covariance assignment")  {
     
     val fUnit = PromiseFlowTermination[Unit]()
      // val fAny: FlowTermination[Any] = fUnit
     implicit val f_ : FlowTermination[_] = fUnit
     
     val qq = implicitly[FlowTermination[_]]
     
   }

   

  val gopherApi = CommonTestObjects.gopherApi
   
}
