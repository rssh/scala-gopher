package gopher.util

import scala.reflect._
import scala.reflect.api._

object ReflectUtil
{


   def retrieveValSymbols[T:u.TypeTag](u:Universe)(ownerType:u.Type): List[u.TermSymbol] =
   {
     val r1 = ownerType.members.filter(_.isTerm).map(_.asTerm).filter(x => x.isVal)
     val signatures = r1.map(_.typeSignature)
     val ut = u.typeOf[T]
     val checkResults = signatures.map( _ <:< u.typeOf[T])
     val retval = ownerType.members.filter(_.isTerm).map(_.asTerm).filter{ x =>
                         if (x.isVal) {
                            // in scala 2.12 getter method type, scala 2.11 - type
                              val r = x.typeSignature match {
                                case u.NullaryMethodType(rt) => rt <:< u.typeOf[T] // for scala-2.12
                                case _ => (x.typeSignature <:< u.typeOf[T])   // for scala-2.11
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
