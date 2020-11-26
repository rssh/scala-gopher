package gopher.goasync

import scala.concurrent._
import scala.async.Async._
import scala.collection.BuildFrom
import scala.language.higherKinds


class AsyncIterable[A,CC[_] <: Iterable[_]](val x:CC[A]) //extends AnyVal [implementation restriction, [scala-2.11.8]
{

   def foreachAsync[U](f: A => Future[U])(implicit ec:ExecutionContext): Future[Unit] =
   async{
     val it: Iterator[A] =  x.asInstanceOf[Iterable[A]].iterator
     while(it.hasNext) {
        val v:A = it.next()
        await(f(v))
     }
   }
 

   def mapAsync[U](f: A => Future[U])(implicit ec:ExecutionContext): Future[CC[U]] =
     async {
      val builder = x.iterableFactory.newBuilder[U] // x.iterableFactory.newBuilder[U]
      val it = x.asInstanceOf[Iterable[A]].iterator
      while(it.hasNext) {
         val v: A = it.next
         builder += await(f(v))
      }
      builder.result().asInstanceOf[CC[U]]
     }

}



class AsyncIterableI[T](val x:Iterable[T]) //extends AnyVal [implementation restriction, [scala-2.11.8]
{


  def foreachAsync[U](f: T => Future[U])(implicit ec:ExecutionContext): Future[Unit] =
    async{
      val it = x.iterator
      while(it.hasNext) {
        await(f(it.next))
      }
    }

  def qq[U](f:T=>U): Unit = {
      x.map(f)
  }

  def mapAsync[F,U,Z](f: T => Future[U])(implicit bf: BuildFrom[F,U,Z], ec:ExecutionContext): Future[Z] =
    async {
      val builder =  bf.newBuilder(x.asInstanceOf[F])  // x.iterableFactory.newBuilder[U]
      val it = x.iterator
      while(it.hasNext) {
        val v = it.next
        builder += await(f(v))
      }
      builder.result()
    }

}
