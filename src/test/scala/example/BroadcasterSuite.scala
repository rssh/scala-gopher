package example.broadcast

/**
 * code from 
 * Concurrent Idioms #1: Broadcasting values in Go with linked channels.
 * https://rogpeppe.wordpress.com/2009/12/01/concurrent-idioms-1-broadcasting-values-in-go-with-linked-channels/
 */

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.{Channel => _, _}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.async.Async._
import gopher._
import gopher.channels._
import CommonTestObjects.gopherApi._
import gopher.tags.Now
import org.scalatest._


class Broadcaster[A]
{
    import Broadcaster._
    import scala.concurrent.ExecutionContext.Implicits.global


    val listenc:   Channel[Channel[Channel[Message[A]]]] = makeChannel()
    val sendc:     Channel[A] = makeChannel()
    val quitc:     Channel[Boolean] = makeChannel()


    val process = select.afold(makeChannel[Message[A]](1)) { (last,s) =>
         s match {
           case v: sendc.read @unchecked =>
                    val next = makeChannel[Message[A]](1)
                    last <~ ValueMessage(next,v)
                    next
           case r: listenc.read @unchecked =>
                    r <~ last
                    last
           case q: quitc.read =>
                    CurrentFlowTermination.exit(last)
         }
     }

      

    def alisten(): Future[Receiver[A]] = go {
      val c = makeChannel[Channel[Message[A]]]()  
      listenc <~ c
      new Receiver(c.read)
    }

}


object Broadcaster {

  import language.experimental.macros
  import scala.reflect.macros.blackbox.Context
  import scala.reflect.api._

  class Receiver[A](initChannel: Channel[Message[A]])
  {

      val out = makeChannel[Option[A]]()

      select.afold(initChannel){ (ch,s) =>
         s match {
           case b: ch.read =>
              ch.awrite(b)
              b match {
                case ValueMessage(nextChannel,v) =>
                         out.write(Some(v))
                         nextChannel
                case EndMessage =>
                         out.write(None)
                         //out.close()
                         select.exit(ch)
              }
         }
      }

     def aread():Future[Option[A]] = out.aread

     def read():Option[A] = macro Receiver.readImpl[A]
  
  }

  object Receiver
  {
    def readImpl[A](c:Context)():c.Expr[Option[A]]=
    {
      import c.universe._
      awaitImpl[Option[A]](c)(c.Expr[Future[Option[A]]](q"${c.prefix}.aread()"))
    }
  }
 
  sealed trait Message[+A]
  case class ValueMessage[A](ch: Channel[Message[A]],v:A) extends Message[A]
  case object EndMessage extends Message[Nothing]

}


class BroadcaseSuite extends AsyncFunSuite
{

  def listen[A](r: Broadcaster.Receiver[A], out:Output[A]): Future[Unit] = go {
       var finish = false;
       while(!finish) {
          val x = await(r.aread)
          x match {
            case Some(m) => out.write(m)
            case None => finish = true
          }
       }
       ()
  }

  def doBroadcast(out:Channel[Int]): Unit = go {

    val b = new Broadcaster[Int]()

    val nSend = new AtomicInteger(0)
    val allDelivered = Promise[Int]()
    def withIncrSend[A](a:A):A =
    {
      val n = nSend.incrementAndGet()
      //wait - until we receive count([1,1,2,2,2])
      if (n==5) allDelivered success 5
      a
    }

    val nout = out.premap(withIncrSend[Int])

    val r1 = await(b.alisten())
    val l1 = listen(r1,nout)
    val r2 = await(b.alisten())
    val l2 = listen(r2,nout)

    b.sendc.write(1)


    val r3 = await(b.alisten())
    val l3 = listen(r3,nout)

    b.sendc.write(2)

    b.quitc.write(true)

    allDelivered.future.map(_ => out.close())
    //Thread.sleep(500)
    //out.close()
  }

  test("broadcast") {
     val channel = makeChannel[Int]()
     doBroadcast(channel)
     val fsum = channel.afold(0){ (s,n) =>
       s+n
     }
     //val fsum = select.afold(0){ (s,sel) =>
     //  sel match {
     //    case n:channel.read => s+n
     //    case _:channel.done => select.exit(s)
     //  }
     //}
     //val sum = Await.result(fsum,10 seconds)
     fsum map { sum =>
       assert(sum == 8)
     }
  }

}



