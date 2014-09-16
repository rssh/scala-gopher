package gopher.channels


import akka.actor._
import scala.concurrent._
import gopher._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._

class IOChannel[A](futureChannelRef: Future[ActorRef], api: GopherAPI) extends Input[A] with Output[A]
{


  def  cbread[B](f: (A, ContRead[A,B]) => Option[Future[Continuated[B]]], flwt: FlowTermination[B] ): Unit = 
     futureChannelRef.foreach( _ ! ContRead(f,this, flwt) )

  private def  contRead[B](x:ContRead[A,B]): Unit =
     futureChannelRef.foreach( _ ! x )

  def  cbwrite[B](f: ContWrite[A,B] => Option[(A,Future[Continuated[B]])], flwt: FlowTermination[B] ): Unit = 
    if (closed) {
     throw new IllegalStateException("channel is closed");
    } else {
     futureChannelRef.foreach( _ ! ContWrite(f,this, flwt) )
    }

  private def contWrite[B](x:ContWrite[A,B]): Unit =
    futureChannelRef.foreach( _ ! x )

  private[this] implicit val ec = api.executionContext

  def isClosed: Boolean = closed

  def close(): Unit =
  {
    futureChannelRef.foreach( _ ! ChannelClose )
    closed=true
  }

  def foreach(f: A=>Unit): Future[Unit] = macro IOChannel.foreachImpl[A]

  def foreachSync(f: A=>Unit): Future[Unit] =
  {
    val ft = PromiseFlowTermination[Unit]
    cbread({
            (a:A, cont: ContRead[A,Unit]) => 
                f(a)
                if (isClosed) ft.doExit(())
                Some(Future successful cont)
           },ft)
    ft.future
  }

  def foreachAsync(f: A=>Future[Unit]): Future[Unit] =
  {
    val ft = PromiseFlowTermination[Unit]
    cbread({
            (a:A, cont: ContRead[A,Unit]) => 
                Some (f(a) transform (
                   u => { if (isClosed) ft.doExit(())
                          cont },
                   e => {ft.doThrow(e)
                         e }
                ))
           },ft)
    ft.future
  }

  private var closed = false
}

object IOChannel
{

  def foreachImpl[A](c:Context)(f:c.Expr[A=>Unit]):c.Expr[Future[Unit]] =
  {
   import c.universe._
   val findAwait = new Traverser {
      var found = false
      override def traverse(tree:Tree):Unit =
      {
       if (!found) {
         tree match {
            case Apply(TypeApply(Select(obj,TermName("await")),objType), args) =>
                   if (obj.tpe =:= typeOf[async.Async.type]) {
                       found=true
                   } else super.traverse(tree)
            case _ => super.traverse(tree)
         }
       }
      }
   }
   f.tree match {
     case Function(valdefs,body) =>
            findAwait.traverse(body)
            if (findAwait.found) {
               val nbody = q"scala.async.Async.async(${body})"
               c.Expr[Future[Unit]](q"${c.prefix}.foreachAsync(${Function(valdefs,nbody)})")
            } else {
               c.Expr[Future[Unit]](q"${c.prefix}.foreachSync(${Function(valdefs,body)})")
            }
     case _ => c.abort(c.enclosingPosition,"function expected")
   }
  }

}
