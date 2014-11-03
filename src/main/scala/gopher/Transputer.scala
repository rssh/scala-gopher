package gopherCompilerIssue

trait Transputer
{

 class InPort[A](a:A) 
 {
   var v: A = a
 }
 
 object InPort
 {
  @inline def apply[A](a:A):InPort[A] = new InPort(a) 
 }

 import scala.reflect._
 import scala.reflect.runtime.{universe=>ru}

 def retrieveVals1[T:ru.TypeTag](o:Transputer): List[T] =
   {
     val mirror = ru.runtimeMirror(this.getClass.getClassLoader)
     val im = mirror.reflect(o);
     val termMembers = im.symbol.typeSignature.members.filter(_.isTerm).map(_.asTerm)
     val retval = (termMembers.
                      filter ( x => x.typeSignature <:< ru.typeOf[T] && x.isVal).
                      map ((x:ru.TermSymbol) => im.reflectField(x).get.asInstanceOf[T]) 
     ).toList 
     retval
   }

 def retrievePorts = retrieveVals1[InPort[_]](this)
   
 def recoverFactory: ()=>Transputer

}


trait SelectTransputer extends Transputer  
{


}


trait BingoWithRecover extends SelectTransputer
{

  val inX = InPort[Int](1)
  val inY = InPort[Int](1)

}


class Suite 
{

  test("A") {
    val bingo = { def factory(): BingoWithRecover = new BingoWithRecover {
                        //def api = gopherApi
                        def recoverFactory = factory
                     }
      val retval = factory()
      retval
     }

     val bingo1 = bingo.recoverFactory()
     bingo1.retrievePorts
  }

  def test(name:String)(fun: => Unit):Unit =
        fun


}

object Main
{

  def main(args:Array[String]):Unit = {
    val s = new Suite();
  }

}
