package gopher.channels

import scala.reflect.macros.Context


object TypeHelper {

  
  
  def typeTree(c:Context)(tpe:c.Type):c.Tree =
  {
    import c.universe._
 
    
    tpe match {
      case ThisType(sym) => SingletonTypeTree(This(sym.name.toTypeName)) 
      case SingleType(tp, sym) =>
          tp match {
            case  NoPrefix => SingletonTypeTree(Ident(sym.name.toTypeName))
            case _ => ???
          }
      case SuperType(x) => ???
      case ConstantType(x) =>  ???
      case TypeRef(tt,ct,args) => 
        args match {
          case Nil =>
            ct match {
               case NoPrefix => Ident(ct.name.toTypeName)
               case SingleType(p,x) => Select(typeTermTree(c)(p) ,ct.name)
               case _ =>  SelectFromTypeTree(typeTree(c)(tt),ct.name.toTypeName)
            }
          case list => AppliedTypeTree(typeTree(c)(TypeRef(tt,ct,Nil)),
                                        list map(typeTree(c)(_))
                                       ) 
        }
      case RefinedType(x) => ???  
      case ClassInfoType(parents, decls, clazz) => typeClassSym(c)(clazz)   
      case MethodType(params,resultType) => ???
      case NullaryMethodType(resultType) => ???
      case PolyType(typeParams,resultType) => ???
      case ExistentialType(quantified, underlying) => ???
      case AnnotatedType(annotation,underlying,sym) => ???  
      case TypeBounds(low,hight) => ???
      case BoundedWildcardType(bounds) => ???
      case _ => ???  
    }
  }
  
  def typeTermTree(c:Context)(tpe:c.Type): c.Tree =
  {
    ???
  }
  
  def typeClassSym(c:Context)(sym: c.Symbol): c.Tree =
  {
    import c.universe._
    val parts = sym.fullName.split(".");
    if (parts.size == 1) {
      Ident(newTypeName(parts(0)))
    } else {
      Select(genTermSelect(c)(parts,parts.size-1),newTypeName(parts(parts.size-1)))
    }
  }
  
  private def genTermSelect(c:Context)(parts:Array[String],n:Int):c.Tree =
  {
    import c.universe._
    if (n==0) {
      // impossible.
      throw new IllegalStateException("Impossible: call of genTermSelect with n==0")
    } else if (n == 1) {
      Ident(newTermName(parts(0)))
    } else {
      Select(genTermSelect(c)(parts,n-1),newTermName(parts(n-1)))
    }
  }
  
}