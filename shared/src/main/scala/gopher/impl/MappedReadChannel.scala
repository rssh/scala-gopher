package gopher.impl

import gopher._
import scala.util._
import scala.util.control.NonFatal

class MappedReadChannel[F[_],A, B](internal: ReadChannel[F,A], f: A=> B) extends ReadChannel[F,B] {


  class MReader(nested: Reader[B]) extends Reader[A] {

    def wrappedFun(fun: (Try[B] => Unit) ): (Try[A] => Unit) = {
      case Success(a) =>
        try
          val b = f(a)
          fun(Success(b))
        catch
          case NonFatal(ex) =>
            fun(Failure(ex))
      case Failure(ex) =>
        fun(Failure(ex))
    }

    //TODO: think, are we want to pass error to the next level ?
    override def capture(): Expirable.Capture[Try[A]=>Unit] = 
      nested.capture().map{ fun =>
         wrappedFun(fun)   
      }

    override def canExpire: Boolean = nested.canExpire

    override def isExpired: Boolean = nested.isExpired

    override def markUsed(): Unit = nested.markUsed()

    override def markFree(): Unit = nested.markFree()

  }

  def addReader(reader: Reader[B]): Unit = 
    internal.addReader(MReader(reader))
  
  def addDoneReader(reader: Reader[Unit]): Unit = internal.addDoneReader(reader)

  def gopherApi:Gopher[F] = internal.gopherApi

}

class MappedAsyncReadChannel[F[_],A, B](internal: ReadChannel[F,A], f: A=> F[B]) extends ReadChannel[F,B] {

  def addDoneReader(reader: Reader[Unit]): Unit = internal.addDoneReader(reader)

  class MReader(nested: Reader[B]) extends Reader[A] {

    def wrappedFun(fun: (Try[B] => Unit) ): (Try[A] => Unit) = {
      case Success(a) =>
        gopherApi.spawnAndLogFail(
            try 
              asyncMonad.mapTry(f(a))(fun)
            catch
              case NonFatal(ex) =>
                fun(Failure(ex))
                asyncMonad.pure(())
        )
      case Failure(ex) =>
        fun(Failure(ex))
    }

    //TODO: think, are we want to pass error to the next level ?
    override def capture(): Expirable.Capture[Try[A]=>Unit] = 
      nested.capture().map{ fun =>
         wrappedFun(fun)   
      }

    override def canExpire: Boolean = nested.canExpire

    override def isExpired: Boolean = nested.isExpired

    override def markUsed(): Unit = nested.markUsed()

    override def markFree(): Unit = nested.markFree()

  }

  def addReader(reader: Reader[B]): Unit = 
    internal.addReader(MReader(reader))
  
  def gopherApi:Gopher[F] = internal.gopherApi

}




