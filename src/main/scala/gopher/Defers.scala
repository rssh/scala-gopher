package gopher

import scala.annotation.tailrec
import scala.util._
import scala.reflect.runtime.universe.{Try => _, _}
import java.util.concurrent.atomic._

/**
 * Construction Defers: defer/recover is alternative mechanism to exception handling,
 * simular to one in Go language.
 *
 * We use one hidden in go and goScope construct, but of course it can be used unsugared:
 *
 *<pre>
 *  def parseCsv(f File): Either[String, Seq[Array[Double]]] =
 *   withDefers{ d =>
 *     val in = File.open(f)
 *     d.defer{ in.close() }
 *     val retval = Right{
 *         for( (line, nLine) <- in.lines zip Stream.from(1) ) withDefers { d =>
 *            line.split(",") map (Double.parse(_)) 
 *            d.defer{
 *              d.recover foreach {
 *                 case ex: NumberFormatException =>
 *                           throw new RuntimeExcetion(s"can't parse ${s} in line ${line} file ${file.getPath} ")
 *              }
 *            }
 *         }.toSeq
 *     }
 *     defer {
 *       recover foreach {
 *        case ex: Throwable => Left(ex.getMessage)
 *     }
 *     retval
 *   }
 *  }
 *</pre>
 **/
class Defers[T]
{

  /**
   * can be used in main block
   * (which can be plain or async)
   * and store body for defered execution after
   * evaluation of main block
   **/
  def defer(body: =>Unit): Unit =
  {
   var prev = rl.get
   var next = (()=>body)::prev
   while(!rl.compareAndSet(prev,next)) {
      prev = rl.get
      next = (()=>body)::prev
   } 
  }

  /**
   * called after execution of main block, where
   * all 'defered' blocks will be executed in one thread
   * in LIFO order.
   */
  def processResult(x: Try[T]):T =
    { value = x
      unroll(rl.getAndSet(List())) match {
        case Success(v) => v
        case Failure(ex) => 
                  if (ex.isInstanceOf[Defers.NoResultException] &&
                     ( weakTypeOf[T] =:= weakTypeOf[Unit] )) {
                           ().asInstanceOf[T]
                  } else {
                    throw ex
                  }
      }
    } 

  /**
   * called inside defer blocks, where argument(t)
   */
  def recover(): Option[Throwable] = {
      value match {
         case Success(x) => None
         case Failure(ex) => 
                    value = Failure(Defers.NoResultException())
                    Some(ex)
      }
  }

  /**
   * current named result.
   */
  def result: T =
   value match {
     case Success(t) => t
     case Failure(ex) => throw ex
   }

  /**
   * set current named result.
   */
  def result_=(t: T): Unit =
  {
    value = Success(t)
  }

  @tailrec
  private[this] def unroll(l: List[()=>Unit]):Try[T] =
   l match {
     case Nil => value
     case head::tail => try {
                           head()
                         } catch {
                           case ex: Throwable => value = Failure(ex)
                         }
                          // first component is for defer inside defer
                         unroll(rl.getAndSet(Nil) ++ tail)
   }

  private[this] var value: Try[T] = Failure(Defers.NoResultException())

  private[this] val rl: AtomicReference[List[()=>Unit]] = new AtomicReference(List())
}

object Defers
{
  class NoResultException extends RuntimeException

  object NoResultException
  {
   def apply() = new NoResultException()
  }
}

/**
 * syntax sugar, for calling Defers.
 */
object withDefer
{

   def apply[A](f: Defers[A] => A):A =
   { val d = new Defers[A]()
     d.processResult(Try(f(d)))
   }

}

