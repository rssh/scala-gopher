package gopher.channels

import scala.annotation._
import scala.concurrent._
import scala.util._
import java.util.concurrent.atomic.AtomicBoolean

import gopher._

/**
 * Input, which combine two other inputs.
 *
 * can be created with '|' operator.
 *
 * {{{
 *   val x = read(x|y)
 * }}}
 */
class OrInput[A](x:Input[A],y:Input[A]) extends Input[A]
{


  def  cbread[B](f: ContRead[A,B] => Option[ContRead.In[A] => Future[Continuated[B]]], flwt: FlowTermination[B] ): Unit =
  {
    val cBegin = new AtomicBoolean(false)
    val cEnd = new AtomicBoolean(false)

    @tailrec
    def cf(cont:ContRead[A,B]): Option[ContRead.In[A]=>Future[Continuated[B]]] =
    {
                     if (cBegin.compareAndSet(false,true)) {
                        f(cont) match {
                          case sf1@Some(f1) => cEnd.set(true)
                                               sf1
                          case None => cBegin.set(false)
                                       None
                        }
                     } else if (cEnd.get()) {
                        None
                     } else {
                        // own spin-lock: wait until second instance will completely processed.
                        //   this is near impossible situation: when both inputs and outputs
                        //   raise signal at the same time, A start check aviability of handler,
                        //   B enter the section when aviability checking is not finished.
                        while(cBegin.get() && !cEnd.get()) {
                            // TODO: slip tick ?
                            Thread.`yield`();
                        }
                        if (cEnd.get()) None else cf(cont)
                     }
    }
    x.cbread(cf, flwt)
    y.cbread(cf, flwt)
  }

  // | is left-associative, so (x|y|z|v).api better be v.api,  
  def api = y.api

  override def toString() = s"(${x}|${y})"

}

