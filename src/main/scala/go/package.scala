
import language.experimental.macros
import scala.concurrent.Future
import scala.reflect.macros.Context

package object go
{

  def _go[A](x:A):Future[A] = macro goImpl[A]

  def goImpl[A](c:Context)(x: c.Expr[A]):c.Expr[Future[A]] =
  {
   import c.universe._
   System.err.println("goImpl, x="+x)
   System.err.println("goImpl, row="+showRaw(x))
   //
   //  Future {
   //     implicit val _deferrable = new ScopeContext
   //     val select = go.channels.createChannels
   //     try {
   //       transform(t, _deferrable)
   //     } catch {
   //       ..
   //     }
   //
   //  }
   val tree = Apply(
                Select(Select(Ident(newTermName("scala")), newTermName("concurrent")), newTermName("Future")), 
                List(Block(
                      //ValDef(Modifiers(), newTermName("select"), TypeTree(), Literal(Constant(1))),
                      c.resetAllAttrs(x.tree)
                    )     )
              )
                      
   System.err.println("goImpl, output="+tree)

   c.Expr[Future[A]](tree)           
  }

  val select = channels.SelectorMacroCaller

  object ~>
  {
    def unapply(s: channels.SelectorContext): Option[(channels.InputChannel[Any],Any)] = ???  //macro unapplyImpl
  } 

}
