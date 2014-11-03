package gopher

import scala.language.experimental.macros
import scala.concurrent._
import scala.concurrent.duration._

class GopherAPI

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

 

 def api: GopherAPI

 // internal API.

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
   onResume();
 }

 private[gopher] def beforeRestart(prev: Transputer) 
 {
   if (!(prev eq null)) {
      parent = prev.parent
   }
   //§§onRestart()
 }

 private[gopher] var parent: Option[Transputer] = None


}


object Transputer
{



}

/**
 * Transputer, where dehaviour can be described by selector function
 * 
 **/
trait SelectTransputer extends Transputer  
{

/*
 protected override def onEscalatedFailure(ex: Throwable): Unit =
 {
   super.onEscalatedFailure(ex)
 }

 protected override def onStop(): Unit =
 {
   super.onStop()
 }

 private[gopher] override def beforeResume() : Unit =
 {
   super.beforeResume()
   selectorInit()
 }

 protected var selectorInit: ()=>Unit =
                         { () => throw new IllegalStateException("selectorInit us not initialized yet") }
*/

}


