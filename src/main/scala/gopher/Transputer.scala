package gopher

import scala.language.experimental.macros
import channels._
import scala.concurrent._
import scala.concurrent.duration._
import akka.actor._


/**
 * Reusable unit of application structure, which consists from  
 * set of input ports, set of output ports and behaviour
 *
 */
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
  {
        v.cbwrite(f, ft)
  }

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
 
 def start():Future[Unit] =
 {
   api.transputerSupervisorRef ! TransputerSupervisor.Start(this)
   flowTermination.future
 }

 def goOnce: Future[Unit] 

 /**
  * set recover function 
  **/
 def recover(f: PartialFunction[Throwable,SupervisorStrategy.Directive]): Unit =
  { recoveryFunction = f }

 def api: GopherAPI

 // internal API.

 def resume() 
 {
   onResume();
 }

 def copyState(prev: Transputer): Unit = {}

 
 def copyPorts(prev: Transputer): Unit = 
 {
   import scala.reflect._
   import scala.reflect.runtime.{universe=>ru}
   val mirror = ru.runtimeMirror(this.getClass.getClassLoader)

   def retrieveVals[T:ru.TypeTag](o:Transputer): List[T] =
   {
     val im = mirror.reflect(o);
     val retval = (im.symbol.typeSignature.members.filter(x => x.isTerm).
                                   map (x => x.asTerm).
                                   filter ( x => x.typeSignature <:< ru.typeOf[T] && x.isVal).
                                   map ((x:ru.TermSymbol) => im.reflectField(x).get.asInstanceOf[T]) 
     ).toList 
     retval
   }
   
   def copyVar[T:ClassTag:ru.TypeTag,V:ClassTag](x:T, y: T, varName: String): Unit =
   {
     val imx = mirror.reflect(x);
     val imy = mirror.reflect(y);
     val field = ru.typeOf[T].decl(ru.TermName(varName)).asTerm.accessed.asTerm
     
     val v = imy.reflectField(field).get
     imx.reflectField(field).set(v)
   }

   def copyPorts[T:ru.TypeTag:ClassTag]:Unit =
   {
     val List(newIns, prevIns) = List(this, prev) map (retrieveVals[T](_))
     for((x,y) <- newIns zip prevIns) copyVar(x,y,"v")
   }

   copyPorts[InPort[_]];
   copyPorts[OutPort[_]];
 }


 /**
  * Used for recover failed instances
  */
 def recoverFactory: ()=>Transputer

 /**
  * called when transducer is choose resume durign recovery.
  */
 protected def onResume() { }

 /**
  * called when failure is escalated.
  **/
 protected def onEscalatedFailure(ex: Throwable) { }

 /**
  * called when transputer is stopped.
  */
 protected def onStop() { }

 private[gopher] val recoveryStatistics = Transputer.RecoveryStatistics( )
 private[gopher] val recoveryPolicy = Transputer.RecoveryPolicy( )
 private[gopher] var recoveryFunction: PartialFunction[Throwable, SupervisorStrategy.Directive] = PartialFunction.empty /* {
                                            case ex: ChannelClosedException => SupervisorStrategy.Stop                       
                                        }
                                        */
 private[gopher] var parent: Option[Transputer] = None
 private[gopher] var flowTermination: PromiseFlowTermination[Unit] = createFlowTermination()

 private[this] def createFlowTermination() = new PromiseFlowTermination[Unit]() {

    override def doThrow(e:Throwable): Unit =
    {
      onEscalatedFailure(e)
      super.doThrow(e)
    }
    
    override def doExit(a:Unit): Unit =
    {
      super.doExit(()) 
      onStop()
    }


 }
 
 import akka.event.LogSource
 implicit def logSource: LogSource[Transputer] = new LogSource[Transputer] {
    def genString(t: Transputer) = t.toString
 }

}

object Transputer
{



 case class RecoveryStatistics(
    var nFailures: Int = 0,
    var windowStart: Long = 0,
    var firstFailure: Option[Throwable] = None,
    var lastFailure: Option[Throwable] = None
 ) {

     def failure(ex: Throwable, recoveryPolicy: RecoveryPolicy, nanoNow: Long): Boolean =
     {
       val same = sameWindow(recoveryPolicy, nanoNow)
       nFailures +=1
       if (firstFailure.isEmpty) {
           firstFailure = Some(ex)
       }
       lastFailure = Some(ex)
       return (same && nFailures >= recoveryPolicy.maxFailures) 
     }
     

     def sameWindow(recoveryPolicy: RecoveryPolicy, nanoNow: Long): Boolean =
     {
       if ((nanoNow - windowStart) > recoveryPolicy.windowDuration.toNanos) {
            nFailures = 0
            windowStart = nanoNow
            firstFailure = None
            lastFailure = None
            false
       } else {
            true
       }
     }

 }
   

 case class RecoveryPolicy(
    var maxFailures: Int = 9999,
    var windowDuration: Duration = 10 seconds
 )

 class TooManyFailures(t: Transputer) extends RuntimeException(s"Too many failures for ${t}", t.recoveryStatistics.firstFailure.get)
 {
   addSuppressed(t.recoveryStatistics.lastFailure.get) 
 }



}

/**
 * Transputer, where dehaviour can be described by selector function
 * 
 **/
trait SelectTransputer extends ForeverSelectorBuilder with Transputer  
{

 /**
  * configure loop in selector
  */
 def loop(f: PartialFunction[Any,Unit]): Unit = macro SelectorBuilder.loopImpl[Unit]

 /**
  * When called inside loop - stop execution of selector, from outside - terminate transformer
  */
 def stop():Unit = stopFlowTermination() 

 private[this] def stopFlowTermination(implicit ft:FlowTermination[Unit] = flowTermination): Unit =
                    ft.doExit(())

 protected override def onEscalatedFailure(ex: Throwable): Unit =
 {
   super.onEscalatedFailure(ex)
   selector.throwIfNotCompleted(ex)
 }

 protected override def onStop(): Unit =
 {
   super.onStop()
   if (!selector.isCompleted) {
      selector.doExit(())
   }
 }

 def goOnce: Future[Unit] = selectorRun

}

class ParTransputer(override val api: GopherAPI, childs:Seq[Transputer]) extends Transputer
{

   childs.foreach(_.parent = Some(this))

   def goOnce: Future[Unit] = {
     implicit val ec: ExecutionContext = api.executionContext
     @volatile var inStop = false
     def withStopChilds[A](f: Future[A]):Future[A] =
     {
      f.onComplete{ _ =>
        if (!inStop) {
           inStop = true
           stopChilds()
        }
      }
      f
     }
     withStopChilds(
         Future.sequence(childs map( x=> withStopChilds(x.start()) ) ) 
     ) map (_ => ())
   }
                                                                          
   override def +(p: Transputer) = new ParTransputer(api, childs :+ p)

   private[this] def stopChilds(): Unit =
     for(x <- childs if (!x.flowTermination.isCompleted) ) {
        x.flowTermination.doExit(())
     }
   
   
   def recoverFactory: () => Transputer = () => new ParTransputer(api,childs)
}


