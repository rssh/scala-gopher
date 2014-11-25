package gopher.util

import scala.reflect._
import scala.reflect.api._

object ReflectUtil
{


   def retrieveValSymbols[T:u.TypeTag](u:Universe)(ownerType:u.Type): List[u.TermSymbol] =
   {
     ownerType.members.filter(_.isTerm).map(_.asTerm).filter( x =>
                                          x.isVal && x.typeSignature <:< u.typeOf[T]
     ).toList
   }


   def retrieveVals[T:ru.TypeTag,O:ClassTag](ru:Universe)(mirror: ru.ReflectiveMirror, o:O): List[T] =
   {
     val im = mirror.reflect(o);
     retrieveValSymbols(ru)(im.symbol.typeSignature) map (im.reflectField(_).get.asInstanceOf[T])     
   }


}
