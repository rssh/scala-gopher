package example.broadcast

/**
 * code from 
 * Concurrent Idioms #1: Broadcasting values in Go with linked channels.
 * https://rogpeppe.wordpress.com/2009/12/01/concurrent-idioms-1-broadcasting-values-in-go-with-linked-channels/
 */

import scala.concurrent.{Channel=>_,_}
import scala.concurrent.ExecutionContext.Implicits.global
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

    val process = select.afold(makeChannel[Message[A]]()) { (last,s) =>
         s match {
           case v: sendc.read @unchecked =>
                    val next = makeChannel[Message[A]]()
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


    def write(a: A) = sendc.awrite(a)
  
    def stop() = quitc.awrite(true)

}


object Broadcaster {

  import language.experimental.macros
  import scala.reflect.macros.blackbox.Context
  import scala.reflect.api._

  class Receiver[A](var c: Channel[Message[A]])
  {

     /**
      * return Some(a) when broadcaster is not closed; None when closed.
      * (this is logic from original Go example, where 
      * 'T' in Go is equilend to Option[T] in Scala [Go nil ~ Scala None])
      * In real life, interface will be better.
      **/
     def aread():Future[Option[A]] = go {
       val b = c.read
       c.write(b)
       b match {
          case ValueMessage(ch,v) =>
                 c = ch
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

  def listen[A](r: Broadcaster.Receiver[A]): Unit = go {
     val x = await(r.aread)

  }

  test("broadcast") {

    val b = new Broadcaster[Int]()


  }

}



