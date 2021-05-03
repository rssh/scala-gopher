package gopher.impl

import cps._
import gopher._
import scala.util._
import scala.util.control._

class ChFlatMappedTryReadChannel[F[_], A, B](prev: ReadChannel[F,Try[A]], f: Try[A]=>ReadChannel[F,Try[B]]) extends ReadChannel[F,Try[B]] {

  def addReader(reader: Reader[Try[B]]): Unit = 
    bChannel.addReader(reader)


  def addDoneReader(reader: Reader[Unit]): Unit = {
    bChannel.addDoneReader(reader)
  }

  def gopherApi:Gopher[F] = prev.gopherApi

  val bChannel = gopherApi.makeChannel[Try[B]]()

  def run(): F[Unit] = {
    given CpsSchedulingMonad[F] = gopherApi.asyncMonad
    async[F]{
       while{ 
         prev.optRead() match
          case None => false
          case Some(v) =>
              val internal: ReadChannel[F,Try[B]] = 
                try 
                  f(v)
                catch 
                  case NonFatal(ex) => 
                    ReadChannel.fromValues[F,Try[B]](Failure(ex))(using gopherApi)
              while{
                internal.optRead() match
                  case None => false
                  case Some(v) => 
                    bChannel.write(v)
                    true
              } do ()
              true
       } do ()
       bChannel.close()
    }
  } 

  gopherApi.asyncMonad.spawn(run())


}
