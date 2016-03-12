package example.broadcast

/**
 * code from 
 * Concurrent Idioms #1: Broadcasting values in Go with linked channels.
 * https://rogpeppe.wordpress.com/2009/12/01/concurrent-idioms-1-broadcasting-values-in-go-with-linked-channels/
 */

import scala.concurrent.{Channel=>_,_}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import scala.async.Async._

import gopher._
import gopher.channels._
import CommonTestObjects.gopherApi._

import org.scalatest._


class Broadcaster[A]
{
    import Broadcaster._

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
     val current = makeEffectedChannel(initChannel)

     /**
      * return Some(a) when broadcaster is not closed; None when closed.
      * (this is logic from original Go example, where 
      * 'T' in Go is equilend to Option[T] in Scala [Go nil ~ Scala None])
      * In real life, interface will be better.
      **/
     def aread():Future[Option[A]] = go {
       val b = current.read
       current.write(b)
       b match {
          case ValueMessage(ch,v) =>
                 current := ch
                 Some(v)
          case EndMessage =>
                 None
       }
     }

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


class BroadcaseSuite extends FunSuite
{

  def listen[A](r: Broadcaster.Receiver[A],out:Output[A]): Future[Unit] = go {
       var finish = false;
       while(!finish) {
          val x = await(r.aread)
          // can't use foreach inside 'go' block.
          if (!x.isEmpty) {
             out.write(x.get)
          } else {
             finish = true
          }
       }
       ();
  }

  def doBroadcast(out:Channel[Int]): Unit = go {

    val b = new Broadcaster[Int]()

    val r1 = await(b.alisten())
    val l1 = listen(r1,out)
    val r2 = await(b.alisten())
    val l2 = listen(r2,out)

    b.sendc.write(1)

    val r3 = await(b.alisten())
    val l3 = listen(r3,out)

    b.sendc.write(2)

    b.quitc.write(true)

    Thread.sleep(500)
    out.close()
  }

  test("broadcast") {
     val channel = makeChannel[Int]()
     doBroadcast(channel);
     val fsum = channel.afold(0){ (s,n) => s+n }
     val sum = Await.result(fsum,10 seconds)
     assert(sum==8)
  }

}



