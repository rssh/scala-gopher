
import scala.language.experimental.macros

package object gopher {


import scala.concurrent._
import gopher.channels._
import gopher.goasync._

//
// magnetic arguments for selector-builder unsugared API
//

 implicit def toAsyncFullReadSelectorArgument[A,B](
                   f: ContRead[A,B] => Option[(() => A) => Future[Continuated[B]]]
              ): ReadSelectorArgument[A,B] = AsyncFullReadSelectorArgument(f)  

 implicit def toAsyncNoOptionReadSelectorArgument[A,B](
                   f: ContRead[A,B] => ((()=>A)=> Future[Continuated[B]])
               ): ReadSelectorArgument[A,B] = AsyncNoOptionReadSelectorArgument(f)

 implicit def toAsyncNoGenReadSelectorArgument[A,B](
                   f: ContRead[A,B] => (A => Future[Continuated[B]])
               ): ReadSelectorArgument[A,B] = AsyncNoGenReadSelectorArgument(f)

 implicit def toAsyncPairReadSelectorArgument[A,B](
                   f: (A, ContRead[A,B]) => Future[Continuated[B]]
               ): ReadSelectorArgument[A,B] = AsyncPairReadSelectorArgument(f)

 implicit def toSyncReadSelectorArgument[A,B](
                   f: ContRead[A,B] => ((()=>A) => Continuated[B])
               ):ReadSelectorArgument[A,B] = SyncReadSelectorArgument(f)

 implicit def toSyncPairReadSelectorArgument[A,B](
                   f: (A, ContRead[A,B]) => Continuated[B]
               ):ReadSelectorArgument[A,B] = SyncPairReadSelectorArgument(f)



 implicit def toAsyncFullWriteSelectorArgument[A,B](
                   f: ContWrite[A,B] => Option[(A,Future[Continuated[B]])]
              ):WriteSelectorArgument[A,B] = AsyncFullWriteSelectorArgument(f)

 implicit def toAsyncNoOptWriteSelectorArgument[A,B](
                   f: ContWrite[A,B] => (A,Future[Continuated[B]])
              ):WriteSelectorArgument[A,B] = AsyncNoOptWriteSelectorArgument(f)

 implicit def toSyncWriteSelectorArgument[A,B](
                   f: ContWrite[A,B] => (A,Continuated[B])
              ): WriteSelectorArgument[A,B] = SyncWriteSelectorArgument(f)

 implicit def toAsyncFullSkipSelectorArgument[A](
                   f: Skip[A] => Option[Future[Continuated[A]]]
              ):SkipSelectorArgument[A] = AsyncFullSkipSelectorArgument(f)

 implicit def toAsyncNoOptSkipSelectorArgument[A](
                   f: Skip[A] => Future[Continuated[A]]
              ):SkipSelectorArgument[A] = AsyncNoOptSkipSelectorArgument(f)

 implicit def toSyncSelectorArgument[A](
                   f: Skip[A] => Continuated[A]
              ):SkipSelectorArgument[A] = SyncSelectorArgument(f)

//
// Time from time we forgott to set 'go' in selector builder. 
// Let's transform one automatically
//    TODO: make 'go' nilpotent before this. 
//
// implicit def toFuture[A](sb:SelectorBuilder[A]):Future[A] = sb.go

 @scala.annotation.compileTimeOnly("FlowTermination methods must be used inside flow scopes (go, reading/writing/idle args)")
 implicit def compileTimeFlowTermination[A]: FlowTermination[A] = ???

 def go[T](body: T)(implicit ec:ExecutionContext) : Future[T] = macro GoAsync.goImpl[T]

 def goScope[T](body: T): T = macro GoAsync.goScopeImpl[T]

 @scala.annotation.compileTimeOnly("defer/recover method usage outside go / goScope ")
 def defer(x: =>Unit): Unit = ??? 

 @scala.annotation.compileTimeOnly("defer/recover method usage outside go / goScope ")
 def recover[T](f: PartialFunction[Throwable, T]): Boolean = ??? 

}

