package gopher.channels

import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context
import scala.reflect.api._
import gopher._
import gopher.util._
import scala.concurrent._
import scala.annotation.unchecked._



trait FoldSelectorBuilder[T] extends SelectorBuilder[T]
{

   def readingWithFlowTerminationAsync[A](ch: Input[A], f: (ExecutionContext, FlowTermination[T], A) => Future[T] ): this.type =
   {
     def normalized(_cont: ContRead[A,T]):Option[ContRead.In[A]=>Future[Continuated[T]]] =
            Some(ContRead.liftIn(_cont){ a=>
                    f(ec,selector,a) map Function.const(ContRead(normalized,ch,selector))
                })
     withReader[A](ch, normalized) 
   }


}

/**
 * Short name for use in fold signature 
 **/
trait FoldSelect[T] extends FoldSelectorBuilder[T]
{
   //override def api = theApi
}

object FoldSelectorBuilder
{

   /**
    *```
    * selector.afold(s0) { (s, selector) =>
    *    selector.match {
    *      case x1: in1.read => f1
    *      case x2: in2.read => f2
    *      case x3: out3.write if (x3==v) => f3
    *      case _  => f4
    *    }
    * }
    *```
    * will be transformed to
    *{{{
    * var s = s0
    * val bn = new FoldSelector
    * bn.reading(in1)(x1 => f1 map {s=_; s; writeBarrier})
    * bn.reading(in2)(x2 => f2 map {s=_; s; writeBarrier})
    * bn.writing(out3,v)(x2 => f2 map {s=_; s})
    * bn.idle(f4 map {s=_; s})
    *}}}
    *
    * also supported partial function syntax:
    *
    *{{{
    * selector.afold((0,1)){ 
    *    case ((x,y),s) => s match {
    *      case x1: in1.read => f1
    *      case x2: in2.read => f2
    *      case x3: out3.write if (x3==v) => f3
    *      case _  => f4
    *    }
    *}}}
    * will be transformed to:
    *{{{
    * var s = s0
    * val bn = new FoldSelector
    * bn.reading(in1)(x1 => async{ val x = s._1;
    *                              val y = s._2;
    *                              s = f1; writeBarrier} })
    * bn.reading(in2)(x2 => { val x = s._1;
    *                         val y = s._2;
    *                         f2 map {s=_; s; writeBarrier} })
    * bn.writing(out3,{val x1=s._1
    *                  val x2=s._2
    *                  v})(x2 => f2 map {s=_; s})
    *}}}
    **/
   def foldImpl[S](c:Context)(s:c.Expr[S])(op:c.Expr[(S,FoldSelect[S])=>S]):c.Expr[S] =
   {
    import c.universe._
    val (stateVar,selectVar,cases) = parseFold(c)(op)
    System.err.println("stateVar:"+stateVar)
    System.err.println("selectVar:"+selectVar)
    System.err.println("cases:")
    for(c <- cases){
      System.err.println(c)
      System.err.println(showRaw(c))
    }
    val bn = c.freshName("fold")
    val tree = q"""
          { 
           val ${bn} = new FoldSelect(${c.prefix})
           ${bn}.transformFold(op)
          }
    """
    c.Expr[S](tree)
   }

   def parseFold[S](c:Context)(op: c.Expr[(S,FoldSelect[S])=>S]): (c.Tree, c.Tree, List[c.Tree]) = 
   {
    import c.universe._
    op.tree match {
       case Function(List(x,y),Match(choice,cases)) => 
                         System.err.println("choice="+choice+", row:"+showRaw(choice))
                         if (choice.symbol != y.symbol) {
                            System.err.println("QQQ")
                            if (cases.length == 1) {
                                cases.head match {
                                 case CaseDef(Apply(TypeTree(),
                                                    List(Apply(TypeTree(),params),Bind(sel,_))),
                                              guard,
                                              Match(Ident(choice1),cases1)) =>
                                   System.err.println("XXX:params="+params)
                                   System.err.println("XXX:params="+showRaw(params))
                                   System.err.println("XXX:sel="+sel)
                                   System.err.println("XXX:sel="+showRaw(sel))
                                   System.err.println("XXX:choice1="+choice1)
                                   System.err.println("XXX:choice1="+showRaw(choice1))
                                   if (sel == choice1) {
                                      System.err.println("yes!")
                                   } else {
                                   }
                                   (x,y,cases)
                                 case _ =>
                                    c.abort(op.tree.pos,"match agains selector in pf is expected")
                                }
                            } else {
                                c.abort(op.tree.pos,"partial function in fold must have one case")
                            } 
                         } else {
                           (x,y,cases)
                         }
                       // TODO: check that 'choice' == 'y'
       case Function(params,something) =>
               c.abort(op.tree.pos,"match is expected in select.fold, we have: "+MacroUtil.shortString(c)(op.tree));
       case _ =>
               c.abort(op.tree.pos,"inline function is expected in select.fold, we have: "+MacroUtil.shortString(c)(op.tree));
    }
   }

}


