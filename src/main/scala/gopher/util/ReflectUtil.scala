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


   def retrieveValSymbolsAsMap[T:u.TypeTag,O:ClassTag](u:Universe)(mirror:u.ReflectiveMirror,o:O):Map[u.TermName,u.TermSymbol] =
   {
     val im = mirror.reflect(o);
     val s0=Map[u.TermName,u.TermSymbol]()
     retrieveValSymbols[T](u)(im.symbol.typeSignature).foldLeft(s0){(s,e) =>
        s.get(e.name) match {
           case None => s.updated(e.name,e)
           case Some(x) =>
             throw new IllegalArgumentException("not supported: more than one vals with the same name:"+x.name)
        }
     }
   }


   def retrieveVals[T:ru.TypeTag,O:ClassTag](ru:Universe)(mirror: ru.ReflectiveMirror, o:O): List[T] =
   {
     val im = mirror.reflect(o);
     retrieveValSymbols(ru)(im.symbol.typeSignature) map (im.reflectField(_).get.asInstanceOf[T])     
   }


}
