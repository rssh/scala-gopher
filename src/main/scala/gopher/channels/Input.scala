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

/**
 * Entity, from which we can read objects of type A.
 *
 *
 */
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


  /**
   * async version of read. Immediatly return future, which will contains result of read or failur with StreamClosedException
   * in case of stream is closed.
   */
  def  aread:Future[A] = {
    val ft = PromiseFlowTermination[A]() 
    cbread[A](cont => Some(ContRead.liftIn(cont) {
                                    a => Future.successful(Done(a,ft))
                                 }), ft)
    ft.future
  }


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

  def map[B](g: A=>B): Input[B] = 
     new Input[B] {

        def  cbread[C](f: ContRead[B,C] => Option[ContRead.In[B]=>Future[Continuated[C]]], ft: FlowTermination[C] ): Unit =
        {
         def mf(cont:ContRead[A,C]):Option[ContRead.In[A]=>Future[Continuated[C]]] =
         {  val contA = ContRead(f,this,cont.flowTermination)
            f(contA) map (f1 => { case v@ContRead.Value(a) => f1(ContRead.Value(g(a)))
                                  case ContRead.Skip => Future successful cont
                                  case ContRead.ChannelClosed => f1(ContRead.ChannelClosed)
                                  case ContRead.Failure(ex) => f1(ContRead.Failure(ex))
                                } )
         }
         thisInput.cbread(mf,ft)
        }

     }


  def zip[B](x: Iterable[B]): Input[(A,B)] = ???

  def zip[B](x: Input[B]): Input[(A,B)] = ???

  def flatMapOp[B](g: A=>Input[B])(op:(Input[B],Input[B])=>Input[B]):Input[B] = ???

  def flatMap[B](g: A=>Input[B]):Input[B] = ???

  def seq = new {
    def flatMap[B](g: A=>Input[B]):Input[B] = flatMapOp(g)( _ append _ )
  }

  /**
   * when the first channel is exhaused, read from second.
   **/
  def append(other:Input[A]):Input[A] = ???



  /**
   * duplicate input 
   */
  def dup(): (Input[A],Input[A]) =  ???


  def foreachSync(f: A=>Unit): Future[Unit] =
  {
    val ft = PromiseFlowTermination[Unit]
    lazy val contForeach = ContRead(applyF,this,ft)
    def applyF(cont:ContRead[A,Unit]):Option[ContRead.In[A]=>Future[Continuated[Unit]]] =
          Some( (in:ContRead.In[A]) =>
                 in match {
                   case ContRead.ChannelClosed => Future successful Done((),ft)
                   case x => ContRead.liftIn(cont){ x => f(x)
                                              Future successful contForeach
                                            }(x)
                 }
              )
    cbread(applyF, ft) 
    ft.future
  }

  def foreachAsync(f: A=>Future[Unit])(implicit ec:ExecutionContext): Future[Unit] =
  {
    val ft = PromiseFlowTermination[Unit]
    def applyF(cont:ContRead[A,Unit]):Option[ContRead.In[A]=>Future[Continuated[Unit]]] =
          Some{
                case ContRead.ChannelClosed => Future successful Done((),ft)
                case in =>
                     ContRead.liftIn(cont){ x => f(x) map ( _ => ContRead(applyF, this, ft) ) }(in)
              } 
    cbread(applyF,ft)
    ft.future
  }

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


  def aforeachImpl[A](c:Context)(f:c.Expr[A=>Unit]): c.Expr[Future[Unit]] = ???

  def foldImpl[A,S](c:Context)(s0:c.Expr[S])(f:c.Expr[(S,A)=>S]): c.Expr[S] =
  {
   import c.universe._
   c.Expr[S](q"scala.async.Async.await(${afoldImpl(c)(s0)(f)})")
  }

  def afoldImpl[A,S](c:Context)(s0:c.Expr[S])(f:c.Expr[(S,A)=>S]): c.Expr[Future[S]] = ???


}
