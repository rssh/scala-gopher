package gopher

import scala.language.experimental.macros
import channels._
import scala.concurrent._
import scala.concurrent.duration._


trait Transputer
{

 class InPort[A](input:Input[A]) extends Input[A]
 {

   override def cbread[B](f: ContRead[A,B] => Option[ContRead.In[A] => Future[Continuated[B]]],ft: FlowTermination[B]): Unit = 
          v.cbread(f,ft)

   def api: gopher.GopherAPI = Transputer.this.api

   def connect(x: Input[A]): Unit = 
      { v=x }

   def connect(outPort: Transputer#OutPort[A], bufferSize:Int = 1): Unit = 
     {
       val ch = api.makeChannel[A](bufferSize)
       v = ch
       outPort.v = ch
     } 

   def  <~~<(x: Transputer#OutPort[A]) = connect(x)

   var v: Input[A] = input
 }
 
 object InPort
 {
  @inline def apply[A]():InPort[A] = new InPort(null) // TODO: create special non-initialized class.
 }

 class OutPort[A](output:Output[A]) extends Output[A]
 {
  override def cbwrite[B](f: ContWrite[A,B] => Option[(A, Future[Continuated[B]])], ft: FlowTermination[B]): Unit =
        v.cbwrite(f, ft)

  def api: gopher.GopherAPI = Transputer.this.api

  def connect(x: Output[A]): Unit = 
      { v=x }

  def connect(inPort: Transputer#InPort[A], bufferSize:Int = 1): Unit = 
  {
   val ch = api.makeChannel[A](bufferSize)
   v = ch
   inPort.v = ch
  }

  def >~~> (x: Transputer#InPort[A]) = connect(x)

  var v: Output[A] = output
 }

 object OutPort
 {
  @inline def apply[A]():OutPort[A] = new OutPort(null) // TODO: create special non-initialized class.
 }

 def +(p: Transputer) = new ParTransputer(api, Seq(this,p))
 
 def go: Future[Unit]

 def api: GopherAPI

 /**
  * Used for recover failed instance.
  */
 def recoverFactory: ()=>Transputer
}

trait SelectTransputer extends ForeverSelectorBuilder with Transputer with FlowTermination[Unit] 
{

 def loop(f: PartialFunction[Any,Unit]): Unit = macro SelectorBuilder.loopImpl[Unit]

 // TODO: move to macro
 def stop() = doExit(())

 @inline def doExit(a: Unit): Unit = selector.doExit(a)
 @inline def doThrow(e: Throwable): Unit = selector.doThrow(e)
 @inline def isCompleted: Boolean = selector.isCompleted
 @inline def throwIfNotCompleted(ex: Throwable): Unit = selector.throwIfNotCompleted(ex)

}

class ParTransputer(override val api: GopherAPI, childs:Seq[Transputer]) extends Transputer
{
   def go: Future[Unit] = {
     implicit val ec: ExecutionContext = api.executionContext
     Future.sequence(childs map(_.go)) map (_ => ())
   }
                                                                          
   override def +(p: Transputer) = new ParTransputer(api, childs :+ p)

   def recoverFactory: () => Transputer = () => new ParTransputer(api,childs)
}


