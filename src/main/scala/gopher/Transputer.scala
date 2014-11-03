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


 private[gopher] var parent: Option[Transputer] = None


}


trait SelectTransputer extends Transputer  
{


}




trait BingoWithRecover extends SelectTransputer
{

  val inX = InPort[Int](1)
  val inY = InPort[Int](1)
  val out = OutPort[Boolean](false)
  val fin = OutPort[Boolean](false)

}


class Suite 
{

  test("A") {
    val bingo = { def factory(): BingoWithRecover = new BingoWithRecover {
                        def api = gopherApi
                        def recoverFactory = factory
                     }
      val retval = factory()
      retval
     }

     val bingo1 = bingo.recoverFactory()
     bingo1.copyPorts(bingo)
  }

  def test(name:String)(fun: => Unit):Unit =
        fun

  def gopherApi = new GopherAPI()

}

object Main
{

  def main(args:Array[String]):Unit = {
    val s = new Suite();
    //s.fun
  }

}
