package gopher.impl

import cps._
import gopher._
import scala.util._

class ChFlatMappedReadChannel[F[_], A, B](prev: ReadChannel[F,A], f: A=>ReadChannel[F,B]) extends ReadChannel[F,B] {

  def addReader(reader: Reader[B]): Unit = 
     bChannel.addReader(reader)


  def addDoneReader(reader: Reader[Unit]): Unit = {
     bChannel.addDoneReader(reader)
  }

  def gopherApi:Gopher[F] = prev.gopherApi

  val bChannel = gopherApi.makeChannel[B]()

  def run(): F[Unit] = 
    given CpsSchedulingMonad[F] = gopherApi.asyncMonad
    async[F]{
      while{
        prev.optRead match
          case Some(a) =>
            val internal = f(a)
            while{
              internal.optRead match
                case Some(b) => 
                  bChannel.write(b)
                  true
                case None =>
                  false
            } do ()
            true
          case None =>
            false
      } do ()
      bChannel.close()
    }

  gopherApi.asyncMonad.spawn(run())

  

}