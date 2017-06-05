package gopher.channels


import gopher._
import org.scalatest._

import scala.concurrent._
import scala.language.postfixOps

class FlowTerminationSuite extends AsyncFunSuite
{



  test("flowTermination covariance assignment")  {

    val fUnit = PromiseFlowTermination[Unit]()
    // val fAny: FlowTermination[Any] = fUnit
    implicit val f_ : FlowTermination[_] = fUnit

    val qq = implicitly[FlowTermination[_]]

    assert(true)
  }


  test("select with queue type") {
    import gopherApi.{gopherExecutionContext => _, _}

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

    import gopherApi.{gopherExecutionContext => _, _}
    val channel = makeChannel[Int](100)
    var sum = 0
    val f0 = select.forever {
                case x: channel.read => sum += x
                     select.shutdown()
          }
    for {r2 <- channel.awrite(1)
         r3 <- channel.awrite(2)
         r0 <- f0 } yield assert(sum == 1)

  }


  val gopherApi = CommonTestObjects.gopherApi

}


