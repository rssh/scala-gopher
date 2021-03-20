package gopher.impl

import gopher._

import scala.util._
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference



/**
 * Input, which reed from the first channel, and after first channel is closed - from second
 *
 * can be created with 'append' operator.
 *
 * {{{
 *   val x = read(x|y)
 * }}}
 */
case class AppendReadChannel[F[_],A](x: ReadChannel[F,A], y: ReadChannel[F,A]) extends ReadChannel[F,A]:


   override def gopherApi: Gopher[F] = x.gopherApi

   val xClosed: AtomicBoolean = new AtomicBoolean(false)


   class InterceptReader(nested: Reader[A]) extends Reader[A] {

        val inUsage = AtomicBoolean(false) 

        def canExpire: Boolean = nested.canExpire

        def isExpired: Boolean = nested.isExpired

        def capture():Option[Try[A]=>Unit] =
          nested.capture().map{ readFun =>
            {
              case r@Success(a) => if (inUsage.get()) then
                                      nested.markUsed()
                                   readFun(r)
              case r@Failure(ex) => 
                if (ex.isInstanceOf[ChannelClosedException]) then
                   xClosed.set(true)
                   nested.markFree()
                   y.addReader(nested)
                else
                  if (inUsage.get()) then
                    nested.markUsed()
                  readFun(r)
            }
          }

        def markUsed(): Unit =
          inUsage.set(true)

        def markFree(): Unit =
          nested.markFree()

   }

   def addReader(reader: Reader[A]): Unit =
      if (xClosed.get()) {
        y.addReader(reader)
      } else {
        x.addReader(new InterceptReader(reader))
      }


   def addDoneReader(reader: Reader[Unit]): Unit =
     y.addDoneReader(reader)

