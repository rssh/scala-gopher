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

   def mapAsync[U,That](f: T => Future[U])(implicit bf: CanBuildFrom[Iterable[T],U,That], ec: ExecutionContext): Future[That] =
   {
     async {
      val builder = bf.apply()
      val it = x.iterator
      while(it.hasNext) {
         builder += await(f(it.next))
      }
      builder.result()
     }
   }

}

