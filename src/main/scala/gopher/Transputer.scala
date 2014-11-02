package gopher

import scala.language.experimental.macros
import channels._
import scala.concurrent._
import scala.concurrent.duration._
import akka.actor._


trait Transputer
{

 class InPort[A](a:A) // extends Input[A]
 {

   var v: A = a
 }
 
 object InPort
 {
  @inline def apply[A](a:A):InPort[A] = new InPort(a) 
 }

 class OutPort[A](a:A)
 {
  var v: A = a
 }

 object OutPort
 {
  @inline def apply[A](a:A):OutPort[A] = new OutPort(a) 
 }

 def +(p: Transputer) = new ParTransputer(api, Seq(this,p))
 
 def start():Future[Unit] =
 {
   api.transputerSupervisorRef ! TransputerSupervisor.Start(this)
   flowTermination.future
 }

 /**
  * set recover function 
  **/
 def recover(f: PartialFunction[Throwable,SupervisorStrategy.Directive]): Unit =
  { recoveryFunction = f }

 def api: GopherAPI

 // internal API.

 /**
  * copyState from previous instance when transputer is restarted.
  * can be overriden in subclasses (by default: do nothing)
  * 
  * Note, that port connection is restored before call of copyState
  */
 def copyState(prev: Transputer): Unit = {}

 
 def copyPorts(prev: Transputer): Unit = 
 {
   import scala.reflect._
   import scala.reflect.runtime.{universe=>ru}
   val mirror = ru.runtimeMirror(this.getClass.getClassLoader)

   def retrieveVals[T:ru.TypeTag](o:Transputer): List[T] =
   {
     val im = mirror.reflect(o);
     val termMembers = im.symbol.typeSignature.members.filter(_.isTerm).map(_.asTerm)
     val retval = (termMembers.
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

 private[gopher] def beforeResume() 
 {
   flowTermination = createFlowTermination()
   onResume();
 }

 private[gopher] def beforeRestart(prev: Transputer) 
 {
   if (!(prev eq null)) {
      recoveryStatistics = prev.recoveryStatistics
      recoveryPolicy = prev.recoveryPolicy
      recoveryFunction = prev.recoveryFunction
      parent = prev.parent
   }
   //§§onRestart()
 }

 private[gopher] var recoveryStatistics = Transputer.RecoveryStatistics( )
 private[gopher] var recoveryPolicy = Transputer.RecoveryPolicy( )
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
 

/*
 import akka.event.LogSource
 implicit def logSource: LogSource[Transputer] = new LogSource[Transputer] {
    def genString(t: Transputer) = t.getClass.getName
 }
*/

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
    var maxFailures: Int = 10,
    var windowDuration: Duration = 1 second
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

 private[gopher] override def beforeResume() : Unit =
 {
   super.beforeResume()
   selector = new Selector[Unit](api)
   selectorInit()
 }

 protected var selectorInit: ()=>Unit =
                         { () => throw new IllegalStateException("selectorInit us not initialized yet") }

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

   private[gopher] override def beforeResume() : Unit =
   {
       super.beforeResume()
       for(ch <- childs) ch.beforeResume()
   }

}


