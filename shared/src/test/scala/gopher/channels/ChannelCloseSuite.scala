package gopher.channels

import gopher._
import scala.concurrent.Future
import scala.util.Try
import scala.util.Success
import scala.util.Failure

import cps._
import cps.monads.FutureAsyncMonad

import munit._

class ChannelCloseSuite extends FunSuite
{

  import scala.concurrent.ExecutionContext.Implicits.global
  given Gopher[Future] = SharedGopherAPI.apply[Future]()

  test("writing after close is impossile")  {

    val channel = makeChannel[Int](100)

    channel.close()

    val producer = channel.awriteAll(1 to 1000)

    producer.transform{
        case Success(u) => assert("" == "expected ChannelClosedException")
                           Failure(RuntimeException("fail"))
        case  Failure(ex) => assert(ex.isInstanceOf[ChannelClosedException])
              Success("ok")
    }

  }

  def checkThrowWhenWriteClose(name: String, channel: gopher.Channel[Future,Int,Int])(implicit loc:munit.Location)= {
    test(s"in async we must see throw for $name")  {
      channel.close()
      @volatile var catched = false
      @volatile var notCatched = false
      val p = async {
        channel.write(1)
        notCatched=true
      }
      p.recover{
        case ex: ChannelClosedException => catched = true
      }.map(_ => assert(!notCatched && catched))
    }
  }

  checkThrowWhenWriteClose("buffered", makeChannel[Int](100))
  checkThrowWhenWriteClose("unbuffered", makeChannel[Int]())
  checkThrowWhenWriteClose("promise", makeOnceChannel[Int]())
  
  
  test("after close we can read but not more, than was send (buffered)")  {
    val channel = makeChannel[Int](100)
    @volatile var q1, q2 = 0
    val p = async {
      channel <~ 1
      channel.close()
      q1 = channel.read
    }
    val afterClose = p flatMap { _ => async{
      val a = channel.read
      q2 = 2
    } }

    afterClose.transform{
      case Failure(ex) =>
          assert(ex.isInstanceOf[ChannelClosedException])
          Success(())
      case Success(v) =>
          assert("Ok" == "ChannelClosedException")    
          Success(v)
    } map  (_ => {
       assert(q1 == 1 && q2 != 2 )
    })

  }

  test("after close we can read but not more, than was send (unbuffered)")  {
    val channel = makeChannel[Int]()
    @volatile var q1, q2, q3 = 0
    val p = async {
      channel <~ 1
      channel.close()
      q1 = channel.read   // will be unblocked after close and tbrwo exception
    }
    val consumer = async{
      q3 = channel.read   // will be run
      q2 = 2
    } 

    val afterClose = p.flatMap(_ => consumer)

    p.transform{
      case Failure(ex) =>
          assert(ex.isInstanceOf[ChannelClosedException])
          Success(())
      case Success(v) =>
          assert("Ok" == "ChannelClosedException")    
          Success(v)
    } map  (_ => {
       assert(q1 == 0)
       assert(q2 == 2)
       assert(q3 == 1)
    })

  }

  def checkCloseSignal(name: String, channel: gopher.Channel[Future,Int,Int])(implicit loc:munit.Location)= {
    test(s"close signal must be send ($name)")  {
      channel.close()
      @volatile var q = 0
      val fp = async {
        val done = channel.done.read
        q = 1
      }
      fp map (_ => assert(q == 1))
    }
  }

  checkCloseSignal("buffered", makeChannel[Int](100))
  checkCloseSignal("unbuffered", makeChannel[Int](100))

  
  test("awrite to close must produce ChannelClosedFailure in Future") {
    val channel = makeChannel[Int](100)
    channel.close
    var x = 1
    val f0 = async {
      try {
        channel.write(1)
        assert("" == "Here should be unreachange")
      }catch{
        case ex: ChannelClosedException =>
          // all ok
      }
    }
   }
  

}


