package gopher.goasync

import scala.concurrent._
import scala.async.Async._
import scala.collection.generic._


class AsyncIterable[T](val x:Iterable[T]) //extends AnyVal [implementation restriction, [scala-2.11.8]
{


   def foreachAsync[U](f: T => Future[U])(implicit ec:ExecutionContext): Future[Unit] =
   async{
     val it = x.iterator
     while(it.hasNext) {
        await(f(it.next))
     }
   }
 

   def mapAsync[U,Z](f: T => Future[U])(implicit bf: CanBuildFrom[_,U,Z], ec:ExecutionContext): Future[Z] =
     async {
      val builder = bf.apply()
      val it = x.iterator
      while(it.hasNext) {
         val v = it.next
         builder += await(f(v))
      }
      builder.result()
     }

}


