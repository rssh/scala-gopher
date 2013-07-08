package gopher.channels

import language.experimental.macros
import scala.concurrent.Future
import scala.reflect.macros.Context




object SelectorMacroCaller {

  def  foreach(x:SelectorContext => Unit):Unit = macro foreachImpl

  def  run(x:SelectorContext => Unit):Unit = macro foreachImpl

  def foreachImpl(c:Context)(x: c.Expr[SelectorContext=>Unit]):c.Expr[Unit] =
  {
   import c.universe._
   val xtree = x.tree
   System.err.println("foreaxh raw="+showRaw(x))
   /*
   val tree = Block(List(
                 ValDef(Modifiers(), newTermName("f"), TypeTree(), xtree)
                        ), 
                 Apply(Ident(newTermName("f")), 
                         List(Apply(
                               Select(New(
                                  Select(
                                    Select(
                                      Select(Ident(nme.ROOTPKG),
                                             newTermName("gopher")), 
                                      newTermName("channels")), 
                                    newTypeName("SelectorContext"))
                                  ), nme.CONSTRUCTOR), List()))))
   System.err.println("foreach output="+show(tree))
   * 
   */
   val (inForEach, scName) = transformForeachBody(c)(xtree)
   
   //  sc = new SelectorContext()
   val newScTree =  ValDef(Modifiers(),scName, TypeTree(), 
                           Apply(Select(New(Select(Select(Select(
                                                  Ident(nme.ROOTPKG), 
                                                     newTermName("gopher")), 
                                                       newTermName("channels")), 
                                                         newTypeName("SelectorContext"))), 
                                 nme.CONSTRUCTOR), 
                                 List()))
                                 
   //  sc.addListener ....
   val futureName = c.fresh("f")                  
   
   //  val f = sc.go
   val run = ValDef(Modifiers(), newTermName(futureName), TypeTree(), Select(Ident(scName), newTermName("go")) )


   val rtree = Block(List(
                 newScTree,
                 inForEach, 
                 run,
                 reify{ () }.tree  // bug in typechecker - can't accept valdef
                                   // as unit.
               ):_*)

  /*
   val rtree =  Block(
                  ValDef(Modifiers(),scName, TypeTree(), reify{ 1 }.tree ),
                  reify{ () }.tree
                )
  */
                                 
   System.err.println("rtree="+rtree);
   
   val r1 = c.typeCheck(c.resetAllAttrs(rtree), typeOf[Unit], false)
   
   //c.typeCheck(tree, pt, silent, withImplicitViewsDisabled, withMacrosDisabled)
   
   System.err.println("r1.tpe = "+r1.tpe)
   
  
               
   c.Expr[Unit](r1)
 }

  def transformForeachBody(c:Context)(x: c.Tree): (c.Tree, c.TermName) = {
    import c.universe._
    x match {
       case Function(List(ValDef(_,paramName,_,_)),Match(x,cases)) => 
                                                  // TODO: check and pass paramName there
           
                                                  (transformMatch(c)(paramName,x,cases),paramName);
       case _ => {
            // TODO: write hlepr functio which wirite first 255 chars of x raw representation
            c.error(x.pos, "match expected in gopher select loop, we have:"+x);
            System.err.println("raw x:"+c.universe.showRaw(x));
            (x,newTermName("<none>"))
       }
    }  
  }
  
  def transformMatch(c:Context)(scName: c.TermName, x: c.Tree, cases: List[c.Tree]): c.Tree =
  {
    import c.universe._
    //TODO: check that x is ident(s)
    val listeners = (for(cd <- cases) yield {
     cd match { 
      case CaseDef(pattern, guard, body) =>
        System.err.println("pattern raw ="+showRaw(pattern));
        System.err.println("pattern nraw ="+show(pattern));
        pattern match {
          case UnApply(x,l) => 
            System.err.println("Unapply catched, x="+x+", l="+l);
            x match {
              case Apply(Select(obj, t /*TermName("unapply")*/),us) =>
                System.err.println("apply in unapply catched, obj="+obj)
                val tpe = obj.tpe
                if (tpe =:= typeOf[ gopher.~>.type ]) {
                  System.err.println("~> catched !!!")
                  transformAddInputAction(c)(scName, x,l,guard,body);
                } else if (tpe =:= typeOf[ gopher.?.type ]) {
                  transformAddInputAction(c)(scName,x,l,guard,body);
                } else if (tpe =:= typeOf[ gopher.<~.type ]) {
                  transformAddOutputAction(c)(scName,x,l,guard,body);
                } else if (tpe =:= typeOf[ gopher.!.type ]) {
                  transformAddOutputAction(c)(scName,x,l,guard,body);
                } else {
                  c.error(x.pos,"only ~> [?] or <~ [!] operations can be in match in gopher channel loop")
                  body
                }
              case _ => c.error(x.pos, "unknown selector in gopher channel loop" )
              body
            }
          case _ =>
            c.error(pattern.pos, "pattern must be unapply")
            body
        }
      case x => c.error(x.pos,"CaseDef expected, have:"+x.toString)  
        cd
     }
    })
    Block(listeners:_*);
  }

  def transformAddInputAction(c:Context)(sc: c.TermName, x: c.Tree, l: List[c.Tree], guard: c.Tree, body: c.Tree) =
  {
    val (channel, argName) = parseChannelArgs(c)(x,l);
    //  sc.addOutputListener{ channel, argName => { body; true} }
    import c.universe._
   // val channelType = c.
    // TODO: extract channelArgType from channel.
    val channelArgType = Ident(newTypeName("Int"))
    val retval = Apply(
                    Select(Ident(sc), newTermName("addInputAction")), 
                    List(
                        channel,
                        Function(List(ValDef(Modifiers(Flag.PARAM), argName, channelArgType/*TypeTree()*/, EmptyTree)), 
                                 Block(body,Literal(Constant(true)))
                                )
                        )
                 )
    retval;
  }

  def transformAddOutputAction(c:Context)(sc: c.TermName, x: c.Tree, l: List[c.Tree], guard: c.Tree, body: c.Tree) =
  {
    val (channel, argName) = parseChannelArgs(c)(x, l);
    //  channe.addOutputListener{ () => { body; Some(c) } }
    import c.universe._
    // TODO: add guard supports.
    val retval = Apply(
                   Select(Ident(sc), newTermName("addOutputAction")), 
                   List(
                       channel,
                       Function(List(), Block(body, Apply(Ident(newTermName("Some")), List(Ident(argName)))))
                   ))
    retval;
  }
  
  
  
  private def parseChannelArgs(c:Context)(x:c.Tree, l:List[c.Tree]):Pair[c.Tree,c.TermName] =
  {
    import c.universe._
    System.err.println("parseChannelArgs, l="+l);
    l match {
      case List(frs,Bind(snd: TermName,something)) => (frs,snd)
      case _  => c.error(x.pos, "channel unapply list must have exactlry 2 arguments")
         ???
    }
  }
  
  
  
  
}

