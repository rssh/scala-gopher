package gopher.channels

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.experimental.macros
import scala.language.reflectiveCalls
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import scala.util._
import java.util.concurrent.ConcurrentLinkedQueue

import gopher._
import gopher.util._


import java.util.concurrent.atomic._

trait Input[A]
{

  thisInput =>

  type <~ = A
  type read = A

  // TODO: use closed in selector.

  /**
   * apply f, when input will be ready and send result to API processor
   */
  def  cbread[B](f:
            ContRead[A,B]=>Option[
                    ContRead.In[A]=>Future[Continuated[B]]
            ], 
            ft: FlowTermination[B]): Unit


  def  aread:Future[A] = ???

  /**
   * instance of gopher API
   */
  def api: GopherAPI

  /**
   * read object from channel. Must be situated inside async/go/action block.
   */
  def  read:A = macro InputMacro.read[A]

  /**
   * synonym for read.
   */
  def  ? : A = macro InputMacro.read[A]

  def atake(n:Int):Future[IndexedSeq[A]] = ???

  /**
   * run <code> f </code> each time when new object is arrived. Ended when input closes.
   *
   * must be inside go/async/action block.
   */
  def foreach(f: A=>Unit): Unit = macro InputMacro.foreachImpl[A]

  def aforeach(f: A=>Unit): Future[Unit] = macro InputMacro.aforeachImpl[A]

  def filter(p: A=>Boolean): Input[A] = ???

  def withFilter(p: A=>Boolean): Input[A] = filter(p)

  def map[B](g: A=>B): Input[B] = ???

  def flatMapOp[B](g: A=>Input[B])(op:(Input[B],Input[B])=>Input[B]):Input[B] = ???

  def append(other:Input[A]):Input[A] = ???

  def prepend(a:A):Input[A] = ???

  def foreachSync(f: A=>Unit): Future[Unit] = ???

  def foreachAsync(f: A=>Future[Unit])(implicit ec:ExecutionContext): Future[Unit] = ???

  def flatFold(fun:(Input[A],A)=>Input[A]):Input[A] = ???

  /**
   * async incarnation of fold. Fold return future, which successed when channel is closed.
   *Operations withing fold applyed on result on each other, starting with s0.
   *```
   * val fsum = ch.afold(0){ (s, n) => s+n }
   *```
   * Here in fsum will be future with value: sum of all elements in channel until one has been closed.
   **/
  def afold[S,B](s0:S)(f:(S,A)=>S): Future[S] = macro InputMacro.afoldImpl[A,S]

  /**
   * fold opeations, available inside async bloc.
   *```
   * go {
   *   val sum = ch.fold(0){ (s,n) => s+n }
   * }
   *```
   */
  def fold[S,B](s0:S)(f:(S,A)=>S): S = macro InputMacro.foldImpl[A,S]

     
  
  def afoldSync[S,B](s0:S)(f:(S,A)=>S): Future[S] =
  {
    val ft = PromiseFlowTermination[S]
    var s = s0
    def applyF(cont:ContRead[A,S]):Option[ContRead.In[A]=>Future[Continuated[S]]] =
    {
          val contFold = ContRead(applyF,this,ft)
          Some{
            case ContRead.ChannelClosed => Future successful Done(s,ft)
            case ContRead.Value(a) => s = f(s,a)  
                                          Future successful contFold
            case ContRead.Skip => Future successful contFold
            case ContRead.Failure(ex) => Future failed ex
          }
    }
    cbread(applyF,ft)
    ft.future
  }

  def afoldAsync[S,B](s0:S)(f:(S,A)=>Future[S])(implicit ec:ExecutionContext): Future[S] =
  {
    val ft = PromiseFlowTermination[S]
    var s = s0
    def applyF(cont:ContRead[A,S]):Option[ContRead.In[A]=>Future[Continuated[S]]] =
    {
          Some{
            case ContRead.ChannelClosed => Future successful Done(s,ft)
            case ContRead.Value(a) => f(s,a) map { x => 
                                        s = x
                                        ContRead(applyF,this,ft)
                                      }
            case ContRead.Skip => Future successful ContRead(applyF,this,ft)
            case ContRead.Failure(ex) => Future failed ex
          }
    }
    cbread(applyF,ft)
    ft.future
  }

}

object Input
{
   def asInput[A](iterable:Iterable[A], api: GopherAPI): Input[A] = new IterableInput(iterable.iterator, api)

   class IterableInput[A](it: Iterator[A], override val api: GopherAPI) extends Input[A]
   {

     def  cbread[B](f:ContRead[A,B]=>Option[ContRead.In[A]=>Future[Continuated[B]]], ft: FlowTermination[B]): Unit =
      f(ContRead(f,this,ft)) map (f1 => { val next = this.synchronized {
                                                       if (it.hasNext) 
                                                         ContRead.Value(it.next)
                                                       else 
                                                         ContRead.ChannelClosed
                                                     }
                                          api.continue(f1(next),ft)
                                        }
                              )
   }

   def closed[A](implicit gopherApi: GopherAPI): Input[A] = new Input[A] {

     def  cbread[B](f:ContRead[A,B]=>Option[ContRead.In[A]=>Future[Continuated[B]]], ft: FlowTermination[B]): Unit =
      f(ContRead(f,this,ft)) map (f1 => f1(ContRead.ChannelClosed))

     def api = gopherApi
   }

   def one[A](a:A)(implicit gopherApi: GopherAPI): Input[A] = new Input[A] {

     val readed: AtomicBoolean = new AtomicBoolean(false)

     def  cbread[B](f:ContRead[A,B]=>Option[ContRead.In[A]=>Future[Continuated[B]]], ft: FlowTermination[B]): Unit =
      f(ContRead(f,this,ft)) map (f1 => f1(
                                    if (readed.compareAndSet(false,true)) {
                                        ContRead.Value(a) 
                                    }else{
                                        ContRead.ChannelClosed
                                    }))

     def api = gopherApi
   }

}



object InputMacro
{

  def read[A](c:Context):c.Expr[A] =
  {
   import c.universe._
   c.Expr[A](q"{scala.async.Async.await(${c.prefix}.aread)}")
  }

  def foreachImpl[A](c:Context)(f:c.Expr[A=>Unit]): c.Expr[Unit] =
  {
   import c.universe._
   c.Expr[Unit](q"scala.async.Async.await(${aforeachImpl(c)(f)})")
  }


  def aforeachImpl[A](c:Context)(f:c.Expr[A=>Unit]): c.Expr[Future[Unit]] =
  {
   import c.universe._
   f.tree match {
     case Function(valdefs,body) =>
            if (MacroUtil.hasAwait(c)(body)) {
               // TODO: add support for flow-termination (?)
               val nbody = q"scala.async.Async.async(${body})"
               val nfunction = atPos(f.tree.pos)(Function(valdefs,nbody))
               val ntree = q"${c.prefix}.foreachAsync(${nfunction})"
               c.Expr[Future[Unit]](c.untypecheck(ntree))
            } else {
               c.Expr[Future[Unit]](q"${c.prefix}.foreachSync(${f.tree})")
            }
     case _ => c.abort(c.enclosingPosition,"function expected")
   }
  }

  def foldImpl[A,S](c:Context)(s0:c.Expr[S])(f:c.Expr[(S,A)=>S]): c.Expr[S] =
  {
   import c.universe._
   c.Expr[S](q"scala.async.Async.await(${afoldImpl(c)(s0)(f)})")
  }

  def afoldImpl[A,S](c:Context)(s0:c.Expr[S])(f:c.Expr[(S,A)=>S]): c.Expr[Future[S]] =
  {
   import c.universe._
   f.tree match {
     case Function(valdefs,body) =>
            if (MacroUtil.hasAwait(c)(body)) {
               val nbody = atPos(body.pos)(q"scala.async.Async.async(${body})")
               val nfunction = atPos(f.tree.pos)(Function(valdefs,nbody))
               val ntree = q"${c.prefix}.afoldAsync(${s0.tree})(${nfunction})"
               c.Expr[Future[S]](c.untypecheck(ntree))
            } else {
               c.Expr[Future[S]](q"${c.prefix}.afoldSync(${s0.tree})(${f.tree})")
            }
   }
  }


}
