package gopher.impl

import gopher._

import scala.util._

class FilteredReadChannel[F[_],A](internal: ReadChannel[F,A], p: A=>Boolean) extends ReadChannel[F,A]  {
  

  class FilteredReader(nested: Reader[A]) extends Reader[A] {

    def wrappedFun(fun: (Try[A] => Unit) ): (Try[A] => Unit) = {
      case Success(a) =>
        if (p(a))
          fun(Success(a))
      case Failure(ex) =>
        fun(Failure(ex))
    }

    override def capture(): Option[Try[A]=>Unit] = 
      nested.capture().map{ fun =>
         wrappedFun(fun)   
      }

    override def canExpire: Boolean = nested.canExpire

    override def isExpired: Boolean = nested.isExpired

    override def markUsed(): Unit = nested.markUsed()

    override def markFree(): Unit = nested.markFree()

  }

  def addReader(reader: Reader[A]): Unit = 
    internal.addReader(FilteredReader(reader))
  
  def addDoneReader(reader: Reader[Unit]): Unit = internal.addDoneReader(reader)

  def gopherApi:Gopher[F] = internal.gopherApi

}


class FilteredAsyncReadChannel[F[_],A](internal: ReadChannel[F,A], p: A=>F[Boolean]) extends ReadChannel[F,A]  {
  

  class FilteredReader(nested: Reader[A]) extends Reader[A] {

    def wrappedFun(fun: (Try[A] => Unit) ): (Try[A] => Unit) = {
      case Success(a) =>
        gopherApi.asyncMonad.spawn(
          gopherApi.asyncMonad.mapTry(p(a)){
            case Success(v) =>
              if (v) {
                fun(Success(a))
              }
            case Failure(ex) =>
              fun(Failure(ex))
          }
        )
      case Failure(ex) =>
        fun(Failure(ex))
    }

    override def capture(): Option[Try[A]=>Unit] = 
      nested.capture().map{ fun =>
         wrappedFun(fun)   
      }

    override def canExpire: Boolean = nested.canExpire

    override def isExpired: Boolean = nested.isExpired

    override def markUsed(): Unit = nested.markUsed()

    override def markFree(): Unit = nested.markFree()

  }

  def addReader(reader: Reader[A]): Unit = 
    internal.addReader(FilteredReader(reader))
  
  def addDoneReader(reader: Reader[Unit]): Unit = internal.addDoneReader(reader)

  def gopherApi:Gopher[F] = internal.gopherApi

}

