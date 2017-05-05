package gopher.channels


import gopher._
import org.scalatest._

import scala.async.Async._

class ChannelCloseSuite extends AsyncFunSuite
{


  test("writing after close is impossile")  {

    val channel = gopherApi.makeChannel[Int](100)

    channel.close

    val producer = channel.awriteAll(1 to 1000)

    recoverToSucceededIf[ChannelClosedException] {
      producer
    }

  }

  test("in async we must see throw")  {

    val channel = gopherApi.makeChannel[Int](100)
    channel.close
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

  test("after close we can read but not more, than was send")  {
    val channel = gopherApi.makeChannel[Int](100)
    @volatile var q1, q2 = 0
    val p = async {
      channel <~ 1
      channel.close
      q1 = channel.read
    }
    val afterClose = p flatMap { _ => async{
      val a = channel.read
      q2 = 2
    } }
    recoverToSucceededIf[ChannelClosedException] {
      afterClose
    } map (_ => assert(q1 == 1 && q2 != 2 ))
  }

  test("close signal must be send")  {
    val channel = gopherApi.makeChannel[Int](100)
    channel.close
    @volatile var q = 0
    val fp = async {
      val done = channel.done.read
      q = 1
    }
    fp map (_ => assert(q == 1))
  }


  def gopherApi = CommonTestObjects.gopherApi

}


