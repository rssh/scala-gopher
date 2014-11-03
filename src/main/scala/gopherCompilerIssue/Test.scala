package gopherCompilerIssue

trait TBase
{

 class InPort[A](a:A) 
 {
   var v: A = a 
 }
 
 object InPort
 {
  def apply[A](a:A):InPort[A] = new InPort(a) 
 }

 import scala.reflect._
 import scala.reflect.runtime.{universe=>ru}

 def retrieveVals1[T:ru.TypeTag](o:TBase): List[T] =
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
   
 def recoverFactory: ()=>TBase

}

trait Bingo extends TBase
{
  val inX = InPort[Int](1)
}


class Suite 
{

  test("A") {
    val bingo = { def factory(): Bingo = new Bingo {
                        def recoverFactory = factory
                     }
      factory()
     }

     bingo.retrievePorts
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
