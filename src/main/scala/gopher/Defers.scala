package gopher

import scala.annotation.tailrec
import scala.util._
import scala.util.control._
import scala.reflect.runtime.universe.{Try => _, _}
import java.util.concurrent.atomic._

/**
 * Construction Defers: defer/recover is alternative mechanism to exception handling,
 * simular to one in Go language.
 *
 * We use one hidden in go and goScope construct are transformed to withDefers usage with 
 * help of macroses.
 *
 * It is also possible to use one unsugared (as in next example), but this is a bit verbose.
 *  
 *
 *<pre>
 *def parseCsv(fname: String): Either[String, Seq[Seq[Double]]] =
 *  withDefer[Either[String,Seq[Seq[Double]]]]{ d =>
 *    val in = Source.fromFile(fname)
 *    d.defer{ 
 *       var r = d.recover{
 *                 case FileNotFoundException => Left("fileNotFound")
 *               }
 *       if (!r) in.close() 
 *       d.recover {
 *         case ex: Throwable => Left(ex.getMessage)
 *       }
 *    }
 *    val retval:Either[String,Seq[Seq[Double]]] = Right{
 *        for( (line, nLine) <- in.getLines.toSeq zip Stream.from(1) ) yield withDefer[Seq[Double]] { d =>
 *           line.split(",") map { s=> 
 *                                 d.defer{
 *                                  d.recover{
 *                                     case ex: NumberFormatException =>
 *                                       throw new RuntimeException(s"parse error in line \${nLine} file \${fname} ")
 *                                  }
 *                                 }
 *                                 s.toDouble 
 *                               }
 *        }.toSeq
 *      }
 *    retval
 *}
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
    { 
      tryProcess(x) match {
        case Success(v) => v
        case Failure(ex) => 
                    throw ex
      }
    } 

  def tryProcess(x: Try[T]):Try[T] =
  {
      last = x
      unroll(rl getAndSet Nil)
      last  
  }

  /**
   * called inside defer blocks, where argument(t)
   */
  def recover(f: PartialFunction[Throwable,T]): Boolean = {
        var retval = false
        for(e <- last.failed if (f.isDefinedAt(e))) {
            last = Success(f(e))
            retval=true
        }
        retval
  }

  @tailrec
  private[this] def unroll(l: List[()=>Unit]):Try[T] =
   l match {
     case Nil => last
     case head::tail => try {
                           head()
                        } catch {
                           case ex: Throwable => 
                                      last=Failure(ex)
                        }
                        // first component is for defer inside defer
                        unroll(rl.getAndSet(Nil) ++ tail)
   }

  private[this] var last: Try[T] = Failure(Defers.NoResultException())

  private[this] val rl: AtomicReference[List[()=>Unit]] = new AtomicReference(List())
}

object Defers
{
  class RecoverException[T](val v:T) extends RuntimeException with NoStackTrace

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

   def asTry[A](f: Defers[A] => A) =
   { val d = new Defers[A]()
     d.tryProcess(Try(f(d)))
   }

}

