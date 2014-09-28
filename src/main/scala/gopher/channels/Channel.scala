package gopher.channels


import akka.actor._
import akka.pattern._
import scala.concurrent._
import scala.concurrent.duration._
import gopher._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._

class IOChannel[A](futureChannelRef: Future[ActorRef], api: GopherAPI) extends Input[A] with Output[A]
{


  def  cbread[B](f: ContRead[A,B] => Option[(()=>A) => Future[Continuated[B]]], flwt: FlowTermination[B] ): Unit = 
  {
   if (closed) {
     if (closedEmpty) {
         flwt.throwIfNotCompleted(new ChannelClosedException())
     } else {
         futureChannelRef.foreach(_.ask(ClosedChannelRead(ContRead(f,this, flwt)))(10 seconds)
                                          .onFailure{
                                             case e: AskTimeoutException => flwt.doThrow(new ChannelClosedException())  
                                             case other => //TODO: log
                                          }
                                 )
     }
   } else {
     futureChannelRef.foreach( _ ! ContRead(f,this, flwt) )
   }
  }

  private def  contRead[B](x:ContRead[A,B]): Unit =
     futureChannelRef.foreach( _ ! x )

  def  cbwrite[B](f: ContWrite[A,B] => Option[(A,Future[Continuated[B]])], flwt: FlowTermination[B] ): Unit = 
    if (closed) {
      flwt.doThrow(new ChannelClosedException())
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
            (cont: ContRead[A,Unit]) => 
                Some{(gen:()=>A) => 
                     if (isClosed) ft.doExit(())
                     f(gen())
                     Future successful cont}
           },ft)
    ft.future
  }

  def foreachAsync(f: A=>Future[Unit]): Future[Unit] =
  {
    val ft = PromiseFlowTermination[Unit]
    cbread({
            (cont: ContRead[A,Unit]) => 
                Some ((gen: ()=>A) => f(gen()) transform (
                   u => { if (isClosed) ft.doExit(())
                          cont },
                   e => {ft.doThrow(e)
                         e }
                ))
           },ft)
    ft.future
  }

  private var closed = false
  private var closedEmpty = false
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
