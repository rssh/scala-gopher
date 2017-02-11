package gopher.util

import scala.annotation._
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import scala.language.reflectiveCalls


object MacroUtil
{

  /**
   * short representation of tree, suitable for show in 
   * error messages.
   */
  def  shortString(c:Context)(x:c.Tree):String =
  {
   val raw = c.universe.showRaw(x)
   if (raw.length > SHORT_LEN) {
       raw.substring(0,raw.length-3)+"..."
   } else {
       raw
   }
  }

  def skipAnnotation(c:Context)(x: c.Tree):c.Tree =
  {
     import c.universe._
     x match {
        case Annotated(_,arg) => arg
        case _ => x
     }
  }

  def hasAwait(c:Context)(x: c.Tree):Boolean =
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
   findAwait.traverse(x)
   findAwait.found
  }

  /**
   * bug in async/scala-2.12.x
   * async/types generate in state-machine next chunk of code:
   *```
   *    val result: scala.concurrent.Promise[Int] = Promise.apply[Int]();
   *    <stable> <accessor> def result: scala.concurrent.Promise[Int] = stateMachine$macro$1041.this.result;
   *    val execContext: scala.concurrent.ExecutionContext = ..
   *    <stable> <accessor> def execContext: scala.concurrent.Promise[Int] = stateMachine$macro$1041.this.execContext;
   *```
   *  when we attempt untype/type code again, it is not compiled.
   *So, we need to remove result and execContext DefDefs
   **/
  def removeAsyncStateMachineResultDefDef(c:Context)(tree: c.Tree):c.Tree =
  {
   import c.universe._

   val outsideStm  = new Transformer {

      override def transform(tree:Tree):Tree =
         tree match {
           case ClassDef(mods,name,tparams,impl) 
                   if (name.toString.startsWith("stateMachine$")) =>
                      impl match {
                        case Template(parents,self,body) =>
                               ClassDef(mods,name,tparams,
                                           Template(parents,self,removeResultDefDef(body,Nil)))
                        //case _ => // impossible, throw
                      }
           case _ => super.transform(tree)
         }

      @tailrec
      def removeResultDefDef(body:List[Tree],acc:List[Tree]):List[Tree] =
      {
        body match {
          case Nil => acc.reverse
          case head::tail =>
                  val (rest,nacc) = head match {
                    case DefDef(mods,name,tparams,vparamss,tpt,rsh) 
                                          if (name.toString == "result" ||
                                              name.toString == "execContext" ) => (tail,acc)
                    case _ => (tail, transform(head)::acc)
                  }
                  removeResultDefDef(rest,nacc)
        }
      }

   }

   val retval = outsideStm.transform(tree)
   retval
  }

  def cleanUntypecheck(c:Context)(tree:c.Tree):c.Tree =
  {
    if (isScala2_11) {
      c.untypecheck(tree)
    } else if (isScala2_12_0) {
      removeAsyncStateMachineResultDefDef(c)(c.untypecheck(tree))
    } else {
      c.untypecheck(tree)
    }
  }

  val isScala2_11 = util.Properties.versionNumberString.startsWith("2.11.")

  val isScala2_12_0 = util.Properties.versionNumberString.startsWith("2.12.0")


  final val SHORT_LEN = 80
}
