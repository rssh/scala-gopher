package gopher.channels

import org.scalatest._
import gopher._
import gopher.tags._
import akka.actor._
import scala.concurrent._
import scala.concurrent.duration._
import CommonTestObjects._

class SchedulerStartupTest extends FunSuite {

     
    test("scheduler-allocated task mast start")  {

       val scheduler = actorSystem.scheduler
       val p = Promise[Int]()
       //System.err.println("scheduler:"+scheduler)
       val cancelable = scheduler.schedule(
                                           100 milliseconds, 
                                           500 milliseconds
                                         ){
                                           if (!p.isCompleted) p success 0
                                          }(ExecutionContext.Implicits.global)
       val x = Await.result(p.future, 1000 milliseconds)
       assert(x==0)

    }

}
