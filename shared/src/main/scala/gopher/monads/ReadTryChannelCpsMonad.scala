package gopher.monads

import scala.util._
import gopher._
import cps._

import gopher.impl._


given ReadTryChannelCpsMonad[F[_]](using Gopher[F]): CpsAsyncMonad[[A] =>> ReadChannel[F,Try[A]]] with CpsMonadInstanceContext[[A] =>> ReadChannel[F,Try[A]]] with

  type FW[T] = [A] =>> ReadChannel[F,Try[A]]

  def pure[T](t:T): ReadChannel[F,Try[T]] = 
     ReadChannel.fromValues[F,Try[T]](Success(t))

  def map[A,B](fa: ReadChannel[F,Try[A]])(f: A=>B): ReadChannel[F,Try[B]] =
     fa.map{
       case Success(a) =>
           try{
             Success(f(a))
           } catch {
             case ex: Throwable => Failure(ex)
           }
       case Failure(ex) => Failure(ex)
     }

  def flatMap[A,B](fa: ReadChannel[F,Try[A]])(f: A=>ReadChannel[F,Try[B]]): ReadChannel[F,Try[B]] = 
    new ChFlatMappedTryReadChannel(fa,{
      case Success(a) => f(a)
      case Failure(ex) => ReadChannel.fromValues[F,Try[B]](Failure(ex ))
    })

  def flatMapTry[A,B](fa: ReadChannel[F,Try[A]])(f: Try[A] => ReadChannel[F,Try[B]]): ReadChannel[F,Try[B]] = 
    new ChFlatMappedTryReadChannel(fa,f)

  def error[A](e: Throwable): ReadChannel[F,Try[A]] = 
    val r = makeChannel[Try[A]]()
    given fm: CpsSchedulingMonad[F] = summon[Gopher[F]].asyncMonad
    summon[Gopher[F]].spawnAndLogFail{ async[F] {
      r.write(Failure(e))
      r.close()
    } }
    r
    

  def adoptCallbackStyle[A](source: (Try[A]=>Unit) => Unit): ReadChannel[F,Try[A]] = {
    val r = makeOnceChannel[Try[A]]()
    given fm: CpsSchedulingMonad[F] = summon[Gopher[F]].asyncMonad
    val fv = fm.adoptCallbackStyle(source)
    summon[Gopher[F]].spawnAndLogFail{
        fm.flatMapTry( fv ){ tryV =>
           r.awrite(tryV)
        }      
    }
    r
  }



given readChannelToTryReadChannel[F[_]](using Gopher[F]): 
                    CpsMonadConversion[ [A]=>>ReadChannel[F,A], [A]=>>ReadChannel[F,Try[A]]] with

  def apply[T](ft: ReadChannel[F,T]): ReadChannel[F,Try[T]] = ft.map(x => Success(x))
      


