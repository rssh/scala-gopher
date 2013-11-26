package gopher.channels.naive

import language.experimental.macros
import scala.reflect.macros.Context


object SelectorMacroCaller {

  def  foreach(x:SelectorContext => Unit):Unit = macro foreachImpl

  
  def  once = Once
  
  object Once {
    def  foreach(x:SelectorContext => Unit):Unit = macro foreachOnceImpl
  }
  
  //def  run(x:SelectorContext => Unit):Unit = macro foreachImpl

  def foreachImpl(c:Context)(x: c.Expr[SelectorContext=>Unit]):c.Expr[Unit] =
    foreachGenImpl(c)(x,"run")
  
  def foreachOnceImpl(c:Context)(x: c.Expr[SelectorContext=>Unit]):c.Expr[Unit] =
    foreachGenImpl(c)(x,"runOnce")
    
  
  def foreachGenImpl(c:Context)(x: c.Expr[SelectorContext=>Unit], lastOp: String):c.Expr[Unit] =
  {
   import c.universe._
   val xtree = x.tree

   val (inForEach, scName) = transformForeachBody(c)(xtree)
   
   def posName= xtree.pos.show
   
   val newScTree = q"""val ${scName} = new _root_.gopher.channels.naive.SelectorContext( ${posName})
                    """
   
   val run =  Select(Ident(scName),newTermName(lastOp))                              

   val rtree = Block(
                 List(newScTree,inForEach), 
                 run
               )

   val r1 = c.typeCheck(c.resetAllAttrs(rtree), typeOf[Unit], false)
   
   c.Expr[Unit](r1)
 }

  def transformForeachBody(c:Context)(x: c.Tree): (c.Tree, c.TermName) = {
    import c.universe._
    x match {
       case Function(List(ValDef(_,paramName,_,_)),Match(x,cases)) => 
                                                  // TODO: check and pass paramName there
                                                  (transformMatch(c)(paramName,x,cases, Nil),paramName);
       case Function(List(ValDef(_,paramName,_,_)),Block(preCase,Match(caseVar,cases))) =>                                           
                                                 (transformMatch(c)(paramName,x,cases, preCase),paramName);            
       case _ => {
            // TODO: write hlepr functio which wirite first 255 chars of x raw representation
            c.error(x.pos, "match expected in gopher select loop, we have:"+x);
            //System.err.println("raw x:"+c.universe.showRaw(x));
            (x,newTermName("<none>"))
       }
    }  
  }
  
  def transformMatch(c:Context)(scName: c.TermName, x: c.Tree, cases: List[c.Tree], preCase:List[c.Tree]): c.Tree =
  {
    import c.universe._
    //TODO: check that x is ident(s)
    val listeners = (for(cd <- cases) yield {
     cd match { 
      case CaseDef(pattern, guard, body) =>
        pattern match {
          case UnApply(x,l) => 
            x match {
              case Apply(Select(obj, t /*TermName("unapply")*/),us) =>
                // TODO: cjange to syntax matching ?
                val tpe = obj.tpe
                if (tpe =:= typeOf[ gopher.~>.type ]) {
                  transformAddInputAction(c)(scName, x,l,guard,body,preCase);
                } else if (tpe =:= typeOf[ gopher.?.type ]) {
                  transformAddInputAction(c)(scName,x,l,guard,body,preCase);
                } else if (tpe =:= typeOf[ gopher.<~.type ]) {
                  transformAddOutputAction(c)(scName,x,l,guard,body,preCase);
                } else if (tpe =:= typeOf[ gopher.!.type ]) {
                  transformAddOutputAction(c)(scName,x,l,guard,body,preCase);
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
    Block(listeners, reify{ () }.tree );
  }

  def transformAddInputAction(c:Context)(sc: c.TermName, x: c.Tree, l: List[c.Tree], guard: c.Tree, 
                                         body: c.Tree, preBody: List[c.Tree]) =
  {
    
    val (channel, argName, argType) = parseInputChannelArgs(c)(x,l);
    import c.universe._    
      
    val retval = q"""
       ${sc}.addInputAction( ${channel}, (${argName}:${argType}) => scala.async.Async.async{ ${body}; true }  )
    """
    retval
  }

  def transformAddOutputAction(c:Context)(sc: c.TermName, x: c.Tree, l: List[c.Tree], guard: c.Tree, 
                                          body: c.Tree, preBody: List[c.Tree]) =
  {
    val (channel, arg) = parseOutputChannelArgs(c)(x, l);
    import c.universe._
     
   
    // TODO: transform async    
     
    val retval = q"""
      ${sc}.addOutputAction(${channel},{ 
          () => scala.async.Async.async(Some( ${Block(preBody :+ body, arg )}) ) 
      })
    """
    retval;
  }
  
  
  
  private def parseInputChannelArgs(c:Context)(x:c.Tree, l:List[c.Tree]):Tuple3[c.Tree,c.TermName,c.Tree] =
  {
    import c.universe._
    //System.err.println("parseInputChannelArgs, l="+l);
    l match {
      case List(frs,Bind(snd: TermName,typedTree)) => 
          typedTree match {
            case Typed(x,typeTree) => (frs,snd, typeTree)
            case _ => c.abort(typedTree.pos, "type declaration in channel unapply expexted")
          }
      case List(frs,snd) => c.abort(snd.pos, "second argument of channel read in case must be typed")
      case _  => c.abort(x.pos, "channel unapply list must have exactlry 2 arguments")
    }
  }
  
  private def parseOutputChannelArgs(c:Context)(x:c.Tree, l:List[c.Tree]):Tuple2[c.Tree,c.Tree] =
  {
    import c.universe._
    //System.err.println("parseOutpurChannelArgs, l="+l);
    l match {
      case List(frs,snd) => (frs,snd)
      case _  => c.abort(x.pos, "channel unapply list must have exactlry 2 arguments")
    }
  }
  
  
  
}

