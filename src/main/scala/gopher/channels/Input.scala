package gopher.channels

import scala.concurrent._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import scala.util._
import gopher._


/**
 * Entity, which can read (or generate, as you prefer) objects of type A,
 * can be part of channel
 */
trait Input[A]
{

  thisInput =>

  type <~ = A
  type read = A


  /**
   * apply f, when input will be ready and send result to API processor
   */
  def  cbread[B](f: (ContRead[A,B] => (Option[(()=>A) => Future[Continuated[B]]])), flwt: FlowTermination[B] ): Unit


  def  aread:Future[A] = {
    val ft = PromiseFlowTermination[A]() 
    cbread[A]( self => Some((gen:()=>A) => Future.successful(Done(gen(),ft))) , ft )
    ft.future
  }


  /**
   * read object from channel. Must be situated inside async/go/action block
   */
  def  read:A = macro InputMacro.read[A]

  /**
   * synonym for read.
   */
  def  ? : A = macro InputMacro.read[A]


  def atake(n:Int):Future[IndexedSeq[A]] =
  {
    if (n==0) {
      Future successful IndexedSeq()
    } else {
       val ft = PromiseFlowTermination[IndexedSeq[A]]
       var i = 1;
       var r: IndexedSeq[A] = IndexedSeq()
       cbread({(c:ContRead[A,IndexedSeq[A]]) => 
          Some{gen:(()=>A) =>
               i=i+1
               r = r :+ gen()
               if (i<n) {
                  Future successful c
               } else {
                  Future successful Done(r,ft)
               }
             }},ft)
        ft.future
    }
  }

  def foreach(f: A=>Unit): Unit = macro InputMacro.foreachImpl[A]

  def aforeach(f: A=>Unit): Future[Unit] = macro InputMacro.aforeachImpl[A]

  def filter(p: A=>Boolean): Input[A] =
       new Input[A] {

          def  cbread[B](f: (ContRead[A,B] => Option[(()=>A) => Future[Continuated[B]]]), flwt: FlowTermination[B] ): Unit =
           thisInput.cbread[B]( cont => f(cont) map {
                                            f1 => (gen => { val a = gen()
                                                            if (p(a)) f1(()=>a) else Future successful cont 
                                                          } )
                                        }, flwt  )

       }

  def withFilter(p: A=>Boolean): Input[A] = filter(p)

  def map[B](g: A=>B): Input[B] =
     new Input[B] {

        def  cbread[C](f: (ContRead[B,C] => Option[(()=>B) => Future[Continuated[C]]]), flwt: FlowTermination[C] ): Unit =
             {
               val mf: ContRead[A,C] => Option[(()=>A) => Future[Continuated[C]]] = {
                     cont => f(ContRead(f,this,cont.flowTermination)) map ( 
                                f1 => (gen => f1(()=>g(gen())) )
                             )
               }
               thisInput.cbread( mf, flwt )
             }

     }

  def zip[B](x: Iterable[B]): Input[(A,B)] = ???

  def async = new {
  
     def foreach(f: A=> Unit):Future[Unit] = macro InputMacro.aforeachImpl[A]

     @inline
     def foreachSync(f: A=>Unit): Future[Unit] =  thisInput.foreachSync(f)
           
     @inline
     def foreachAsync(f: A=>Future[Unit])(implicit ec:ExecutionContext): Future[Unit] =
                                                  thisInput.foreachAsync(f)(ec)

  }

  def foreachSync(f: A=>Unit): Future[Unit] =
  {
    val ft = PromiseFlowTermination[Unit]
    cbread({
            (cont: ContRead[A,Unit]) => 
                Some{(gen:()=>A) => 
                     {var ar = false
                      try {
                       val a=gen()
                       ar = true 
                       f(a)
                      }catch{
                       case ex: ChannelClosedException if (!ar) => ft.doExit(())
                      }
                      Future successful cont
                    }}
           },ft)
    ft.future
  }

  def foreachAsync(f: A=>Future[Unit])(implicit ec:ExecutionContext): Future[Unit] =
  {
    val ft = PromiseFlowTermination[Unit]
    cbread({
            (cont: ContRead[A,Unit]) => 
                Some ((gen: ()=>A) => 
                          { val next = Try(gen()) match {
                              case Success(a) => f(a) 
                                                 cont
                              case Failure(ex) => if (ex.isInstanceOf[ChannelClosedException]) {
                                                     Done((),ft)
                                                  } else {
                                                     ft.doThrow(ex)
                                                     Never
                                                  } 
                            }
                            Future successful next
                           }
                     )
           },ft)
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


  def aforeachImpl[A](c:Context)(f:c.Expr[A=>Unit]): c.Expr[Future[Unit]] =
  {
   import c.universe._
   val findAwait = new Traverser {
      var found = false
      override def traverse(tree:Tree):Unit =
      {
       if (!found) {
         tree match {
            case Apply(TypeApply(Select(obj,TermName("await")),objType), args) =>
                   if (obj.tpe =:= typeOf[scala.async.Async.type]) {
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
