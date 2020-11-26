package gopher.goasync

import scala.concurrent._
import scala.async.Async._
import scala.collection.generic._


class AsyncOption[T](val x:Option[T]) extends AnyVal 
{


   def foreachAsync[U](f: T => Future[U])(implicit ec:ExecutionContext): Future[Unit] =
   {
     if (x.isDefined) {
        f(x.get) map (_ => ()) 
     } else {
        Future successful (())
     }
   }
 

   def mapAsync[U](f: T => Future[U])(implicit ec:ExecutionContext): Future[Option[U]] =
   {
     if (x.isDefined) {
        f(x.get) map (x => Some(x))
     } else {
        Future successful None
     }
   }

   def flatMapAsync[U](f: T => Future[Option[U]])(implicit ec:ExecutionContext): Future[Option[U]] =
   {
    if (x.isDefined) {
       f(x.get) 
    } else {
        Future successful None
    }
   }

   def filterAsync(f: T=>Future[Boolean])(implicit ec:ExecutionContext): Future[Option[T]] =
   {
     if (x.isDefined) {
       f(x.get) map { r =>
          if (r) x else None
       }
     } else {
        Future successful None
     }
   }

   def filterNotAsync(f: T=>Future[Boolean])(implicit ec:ExecutionContext): Future[Option[T]] =
   {
     if (x.isDefined) {
       f(x.get) map { r => if (r) None else x }
     } else {
        Future successful None
     }
   }


}


