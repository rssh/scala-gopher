package gopher.util

import scala.reflect._
import scala.reflect.api._

object ReflectUtil
{


   def retrieveValSymbols[T:u.TypeTag](u:Universe)(ownerType:u.Type): List[u.TermSymbol] =
   {

      // looks lile type comparison is broken in scala-2.13.3, let's make one themself
     def varLeft(left: u.Type , right: u.Type): Boolean = {
         right match {
           case u.ExistentialType(rightParams, rightBase) =>
              left match {
                case u.TypeRef(leftCn, leftSym, leftParams) =>
                   rightBase match {
                      case u.TypeRef(rightCn, rightSym, rightParams) =>
                        if (leftSym.info <:< rightSym.info) {
                           true
                        } else {
                           left <:< right
                        }
                   }
                case _ =>
                   left <:< right
              }
           case _ =>
              left <:< right
          }
     }

     val retval = ownerType.members.filter(_.isTerm).map(_.asTerm).filter{ x =>
                         // isVar because of scala-2.13 bug: https://github.com/scala/bug/issues/11582
                         if (x.isVal || x.isVar ) {
                            // in scala 2.12 getter method type, scala 2.11 - type
                              val r = x.typeSignature match {
                                case u.NullaryMethodType(rt) => 
                                            varLeft(rt, u.typeOf[T])
                                case _ => 
                                            varLeft(x.typeSignature, u.typeOf[T])
                              }
                              r
                         } else false
     }.toList
     retval
   }


   def retrieveVals[T:ru.TypeTag,O:ClassTag](ru:Universe)(mirror: ru.ReflectiveMirror, o:O): List[T] =
   {
     val im = mirror.reflect(o);
     retrieveValSymbols(ru)(im.symbol.typeSignature) map (im.reflectField(_).get.asInstanceOf[T])     
   }


}
