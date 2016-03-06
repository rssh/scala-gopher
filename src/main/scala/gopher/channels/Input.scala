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

  /**
   * return feature which contains sequence from first `n` elements.
   */
  def atake(n:Int):Future[IndexedSeq[A]] =
  {
    if (n==0) {
      Future successful IndexedSeq()
    } else {
       val ft = PromiseFlowTermination[IndexedSeq[A]]
       @volatile var i = 0;
       @volatile var r: IndexedSeq[A] = IndexedSeq()
       def takeFun(cont:ContRead[A,IndexedSeq[A]]):Option[ContRead.In[A]=>Future[Continuated[IndexedSeq[A]]]] =
       Some{ 
             ContRead.liftIn(cont) { a =>
               i += 1
               r = r :+ a
               if (i<n) {
                  Future successful ContRead(takeFun,this,ft)
               } else {
                  Future successful Done(r,ft)
               }
             }
       }
       api.continuatedProcessorRef ! ContRead(takeFun, this, ft)
       ft.future
    }
  }

  /**
   * run <code> f </code> each time when new object is arrived. Ended when input closes.
   *
   * must be inside go/async/action block.
   */
  def foreach(f: A=>Unit): Unit = macro InputMacro.foreachImpl[A]

  def aforeach(f: A=>Unit): Future[Unit] = macro InputMacro.aforeachImpl[A]

  def filter(p: A=>Boolean): Input[A] =
       new Input[A] {

          def  cbread[B](f:ContRead[A,B]=>Option[ContRead.In[A]=>Future[Continuated[B]]], ft: FlowTermination[B]): Unit =
           thisInput.cbread[B]( 
                               (cont) => f(cont) map {
                                            f1 => { case v@ContRead.Value(a) =>
                                                              if (p(a)) f1(v) else Future successful cont
                                                    case v@_ => f1(v)
                                                  } }, ft)  

           def api = thisInput.api

       }

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

        def api = thisInput.api

     }


  def zip[B](x: Iterable[B]): Input[(A,B)] = zip(Input.asInput(x,api))

  def zip[B](x: Input[B]): Input[(A,B)] = new ZippedInput(api,this,x)

  def flatMapOp[B](g: A=>Input[B])(op:(Input[B],Input[B])=>Input[B]):Input[B] = new Input[B] {

      def  cbread[C](f: ContRead[B,C] => Option[ContRead.In[B]=>Future[Continuated[C]]], ft: FlowTermination[C] ): Unit =
      {
       def mf(cont:ContRead[A,C]):Option[ContRead.In[A]=>Future[Continuated[C]]] =
       { val contA = ContRead(f,this,cont.flowTermination)
           f(contA) map { f1 => {
              case v@ContRead.Value(a) => Future successful ContRead(f,op(g(a),this),cont.flowTermination)
              case ContRead.Skip => Future successful cont
              case ContRead.ChannelClosed => f1(ContRead.ChannelClosed)
              case ContRead.Failure(ex) => f1(ContRead.Failure(ex))
       }}}
       thisInput.cbread(mf,ft)
      }

      def api = thisInput.api
  }

  def flatMap[B](g: A=>Input[B]):Input[B] = flatMapOp(g)( _ or _)

  def seq = new {
    def flatMap[B](g: A=>Input[B]):Input[B] = flatMapOp(g)( _ append _ )
  }

  /**
   * return input merged with 'other'.
   * (i.e. non-determenistics choice)
   **/
  def |(other:Input[A]):Input[A] = new OrInput(this,other)

  /**
   * synonim for non-deteremenistics choice.
   **/
  def or(other:Input[A]):Input[A] = new OrInput(this,other)

  /**
   * when the first channel is exhaused, read from second.
   **/
  def append(other:Input[A]):Input[A] = new Input[A] {

        def  cbread[C](f: ContRead[A,C] => Option[ContRead.In[A]=>Future[Continuated[C]]], ft: FlowTermination[C] ): Unit =
        {
         def mf(cont:ContRead[A,C]):Option[ContRead.In[A]=>Future[Continuated[C]]] =
         {  val contA = ContRead(f,this,cont.flowTermination)
            f(contA) map (f1 => { case v@ContRead.Value(a) => f1(ContRead.Value(a))
                                  case ContRead.Skip => f1(ContRead.Skip) 
                                                       Future successful cont
                                  case ContRead.ChannelClosed => f1(ContRead.Skip) 
                                                       Future successful ContRead(f,other,cont.flowTermination)
                                  case ContRead.Failure(ex) => f1(ContRead.Failure(ex))
                         })
         }
         thisInput.cbread(mf,ft)
        }

        def api = thisInput.api

  }

  def prepend(a:A):Input[A] = new Input[A] {

        val aReaded = new AtomicBoolean(false)

        def  cbread[C](f: ContRead[A,C] => Option[ContRead.In[A]=>Future[Continuated[C]]], ft: FlowTermination[C] ): Unit =
        {
         f(ContRead(f,this,ft)) map { f1 => 
           if (aReaded.compareAndSet(false,true)) {
               f1(ContRead.Value(a))
           } else {
               api.continuatedProcessorRef ! ContRead(f,thisInput,ft)      
               f1(ContRead.Skip)
           }
         }
        }

        def api = thisInput.api

  }


  /**
   * return pair of inputs `(ready, timeouts)`, such that when you read from `ready` you receive element from `this`
   * and if during reading you wait more than specified `timeout`, than timeout message is appear in `timeouts`
   *
   *```
   * val (inReady, inTimeouts) = in withInputTimeouts (10 seconds)
   * select.forever {
   *   case x: inReady.read => Console.println(s"received value \${value}")
   *   case x: inTimeouts.read => Console.println(s"timeout occured")
   * }
   *```
   **/
  def withInputTimeouts(timeout: FiniteDuration): (Input[A],Input[FiniteDuration]) =
                                               new InputWithTimeouts(this,timeout).pair

  /**
   * duplicate input 
   */
  def dup(): (Input[A],Input[A]) = 
        (new DuppedInput(this)).pair

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

  def flatFold(fun:(Input[A],A)=>Input[A]):Input[A] = new Input[A] {
         
      val current = new AtomicReference[Input[A]](thisInput)

      def  cbread[C](f: ContRead[A,C] => Option[ContRead.In[A]=>Future[Continuated[C]]], ft: FlowTermination[C] ): Unit =
      {
        def mf(cont:ContRead[A,C]):Option[ContRead.In[A]=>Future[Continuated[C]]] =
          f(ContRead(f,this,ft)) map { next =>
            { case ContRead.Value(a) => 
                           var changed = false
                           while(!changed) {
                             var prev = current.get
                             var next = fun(prev,a)
                             changed = current.compareAndSet(prev,next) 
                           } 
                           next(ContRead.Value(a))
                         //  fp-version.
                         // next(ContRead.Skip)
                         //ContRead(f, one(a) append (fun(this,a) flatFold fun),ft)
              case v@_ => next(v)
          }   }
        current.get.cbread(mf,ft)
      }

      def api = thisInput.api
     
  }


/*
  def fold[S,B](s0:S)(f:(S,A)=>(S,Option[B])) = new Input[A] {

      def  cbread[C](f: ContRead[A,C] => Option[ContRead.In[A]=>Future[Continuated[C]]], ft: FlowTermination[C] ): Unit =

  }
*/

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
               val nfunction = atPos(f.tree.pos)(Function(valdefs,nbody))
               val ntree = q"${c.prefix}.foreachAsync(${nfunction})"
               c.Expr[Future[Unit]](c.untypecheck(ntree))
            } else {
               c.Expr[Future[Unit]](q"${c.prefix}.foreachSync(${f.tree})")
            }
     case _ => c.abort(c.enclosingPosition,"function expected")
   }
  }


}
