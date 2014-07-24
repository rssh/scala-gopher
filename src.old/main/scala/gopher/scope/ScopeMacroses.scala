package gopher.scope

import language.experimental.macros
import scala.reflect.macros.Context
import scala.reflect.api._



object ScopeMacroses
{

  def goScope[A](x: A) = macro goScopeImpl[A]

  
  def goScopeImpl[A](c:Context)(x: c.Expr[A]): c.Expr[A] =
  {
    import c.universe._
    if (findDefer(c)(x.tree)) {
       withDefer(c)(x)
    } else {
       x
    }
  }

  def withDefer[A](c:Context)(x: c.Expr[A]): c.Expr[A] =
  {
    import c.universe._
    val scName = c.fresh("sc");
    // implicit val sc = new ScopeContext()
    val scDef=ValDef(Modifiers(Flag.IMPLICIT), newTermName(scName), TypeTree(), 
                     Apply(
                          Select(
                               New(
                                   Select(Select(Select(Ident(nme.ROOTPKG), 
                                                         newTermName("gopher")), 
                                                  newTermName("scope")), 
                                            newTypeName("ScopeContext"))
                                            
                                 ), 
                                nme.CONSTRUCTOR
                           ), 
                           List()
                     ))
    //    goScoped(x)
    val goScoped0 = Apply(
                     Select(Select(Select(Ident(nme.ROOTPKG), newTermName("gopher")), 
                                   newTermName("scope")), 
                            newTermName("goScoped")), 
                            List(transformDefer(c)(x.tree,scName)))  
    val goScoped = Apply(goScoped0,List(Ident(newTermName(scName))))                        
                     
    val tree = Block(
                List(
                  scDef
                ),
                goScoped
              )
    c.Expr[A](c.resetAllAttrs(tree))
  } 

  
  
  private def matchGopherCall(c:Context)(x:c.Tree): Option[String] =
  {
     import c.universe._
     object GopherCallMatch
     {
    
       def unapply(x: c.Tree): Option[String] = 
        x match {
          case Select(Select(Ident(cGopher), nme.PACKAGE), cName) =>
                 if (cGopher.decoded == "gopher") Some(cName.decoded) else None
          case TypeApply(x1,List(t)) => unapply(x1) 
          case _ => None       
        }
       
     }
     x match {
       case GopherCallMatch(s) => Some(s)
       case _ => None
     }
     
  }
        
     
    
    def findDefer(c:Context)(x: c.Tree): Boolean = 
    {
    import c.universe._  
    @inline def find(t: c.Tree) = findDefer(c)(t)
    @inline def findl(l: List[c.Tree]) = l.exists(findDefer(c))
    x match {
      case ClassDef(_,_,_,_) => false
      case ModuleDef(_,_,_) => false
      case ValDef(_,_,tpt,rhs) => find(rhs)
      case x: DefDef => false
      case x: TypeDef => false
      case LabelDef(_,_,rhs) => find(rhs)
      case Block(stats, expr) => findl(stats) || find(expr)
      case Match(selector, cases) =>  find(selector) || findl(cases)
      case CaseDef(pat, guard, body) => find(body)
      case Alternative(trees) => false // impossible
      case Function(vparams, body) => find(body)
      case Assign(lhs, rhs) => find(lhs) || find(rhs)
      case AssignOrNamedArg(lhs, rhs) =>  find(rhs)
      case If(cond, thenp, elsep) =>  find(cond) || find(thenp) || find(elsep)
      case Return(expr) => find(expr)
      case Try(block, catches, finalizer) => find(block) || findl(catches) || find(finalizer)
      case Typed(expr, tpt) => find(expr)
      case Apply(fun, args) =>
            val rNow = matchGopherCall(c)(fun) match {
              case Some(x) => x == "defer"
              case None =>  false;
            }
            if (!rNow) {
              find(fun) || findl(args)
            } else rNow
      case Select(qualifier, name) => find(qualifier)
      case Annotated(annot, arg) => find(arg)
      case _ => false

    }
   }
  

 
  /**
   * substitute in x
   * * defer - to sc.pushDefer
   * * panic - to sc.panic
   * * restore - to sc.restore
   */
  def transformDefer(c:Context)(x:c.Tree, scName:String): c.Tree =
  {
    import c.universe._
    @inline def walk(t: c.Tree) = transformDefer(c)(t,scName)
    @inline def walkl(l: List[c.Tree]) = l map(transformDefer(c)(_,scName))
    def generateOneArgScCall(funName: String, args: List[c.Tree]):c.Tree =
     args match {
        case x::Nil => Apply(Select(Ident(newTermName(scName)), newTermName(funName)), walkl(args))
        case _ => Apply(Ident(newTermName(funName)),walkl(args))
     }
    x match {
      case ClassDef(mods,name,tparams,Template(parents,self,body)) =>  
                      ClassDef(mods,name,tparams, Template(parents,self, walkl(body)))
      case ModuleDef(mods,name,Template(parents,self,body)) => 
                      ModuleDef(mods,name,Template(parents,self,walkl(body)))
      case ValDef(mods,name,tpt,rhs) => ValDef(mods,name,tpt,walk(rhs))
      case DefDef(mods,name,tparams,vparamss,tpt,rhs) => DefDef(mods,name,tparams,vparamss,tpt,walk(rhs)) 
      case x: TypeDef => x
      case LabelDef(name,params,rhs) => LabelDef(name,params,walk(rhs))
      case Block(stats, expr) => Block(walkl(stats),walk(expr))
      case Match(selector, cases) =>  Match(walk(selector),cases map { 
        case CaseDef(pat, guard, body) => CaseDef(pat,guard,walk(body)) })
      case CaseDef(pat, guard, body) => CaseDef(pat,guard,walk(body))
      case Alternative(trees) => Alternative(walkl(trees)) // impossible
      case Function(vparams, body) => Function(vparams,walk(body))
      case Assign(lhs, rhs) =>  Assign(walk(lhs),walk(rhs))
      case AssignOrNamedArg(lhs, rhs) =>  AssignOrNamedArg(lhs,walk(rhs))
      case If(cond, thenp, elsep) =>  If(walk(cond), walk(thenp), walk(elsep))
      case Return(expr) => Return(walk(expr))
      case Try(block, catches, finalizer) => Try(walk(block),
                                                 (catches map {case CaseDef(x,y,z)=>CaseDef(x,y,walk(z))}),
                                                 walk(finalizer))
      case Typed(expr, tpt) => Typed(walk(expr),tpt)
      case Template(parents,self,body) => Template(parents,self,walkl(body))
      case Apply(fun, args) => 
            matchGopherCall(c)(fun) match {
              case Some(x) => 
                x match {
                  case "defer" => generateOneArgScCall("pushDefer", args) 
                  case "panic" => generateOneArgScCall("panic", args)
                  case "recover" => generateOneArgScCall("recover", args)
                  case "suppressedExcetions" =>
                                    Select(Ident(newTermName(scName)),newTermName("suppressedExceptions"))
                  case "throwSuppressed" => 
                                    Select(Ident(newTermName(scName)),newTermName("throwSuppressed"))
                  case _ => Apply(fun, walkl(args))                  
                }
              case None => Apply(walk(fun), walkl(args))
            }
      case Select(qualifier, name) => Select(walk(qualifier),name)
      case Annotated(annot, arg) => Annotated(annot,walk(arg))
      case _ => x
    }
    
    
  }

     

}
