package gopher.channels


import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import gopher._
import gopher.util._

import scala.concurrent._
import scala.concurrent.duration._
import scala.annotation.unchecked._
import java.util.function.{BiConsumer => JBiConsumer}

import scala.collection.mutable
import scala.ref.WeakReference


/**
 * effected input inside fold. We know, that exists only one call of
 * reading(FoldSelectorInput), generated in our fold statement. Than
  *
  * TODO: eliminate this class, instead refactoer SelectedReaderImpl to customize
  * generation of reader statement.
 */
trait FoldSelectorEffectedInput[A,B] extends Input[A]
{
  def current: Input[A]
  val foldSelector: FoldSelect[B]
  val index:   Int


  def cbread[C](f: ContRead[A,C] => Option[ContRead.In[A] => Future[Continuated[C]]],ft: FlowTermination[C]): Unit = {
    //currently will be never called,
    current.cbread(f,ft)
  }

}

object FoldSelectorEffectedInput{
 def apply[A,B](foldSelect: FoldSelect[B], index:Int, chFun: ()=>Input[A]): FoldSelectorEffectedInput[A,B]
   = new CFoldSelectorEffectedInput[A,B](foldSelect,index,chFun)
}

class CFoldSelectorEffectedInput[A,B](val foldSelector:FoldSelect[B], val index:Int, chFun: ()=>Input[A]) extends FoldSelectorEffectedInput[A,B]
{
  val api = chFun().api
  def current() = chFun()
}


trait FoldSelectorEffectedOutput[A,B] extends Output[A]
{

  def current: Output[A]
  def foldSelector: FoldSelect[B]
  def index: Int

  override def cbwrite[C](f: (ContWrite[A, C]) => Option[(A, Future[Continuated[C]])], ft: FlowTermination[C]): Unit =
  {
    current.cbwrite(f,ft)
  }

}

object FoldSelectorEffectedOutput
{
  def apply[A,B](foldSelect: FoldSelect[B], index: Int, chFun: ()=>Output[A]):FoldSelectorEffectedOutput[A,B]=
       new CFoldSelectorEffectedOutput(foldSelect, index, chFun)
}

class CFoldSelectorEffectedOutput[A,B](val foldSelector: FoldSelect[B], val index: Int, chFun:()=>Output[A]) extends FoldSelectorEffectedOutput[A,B]
{
  val api = chFun().api
  override def current = chFun()
}

trait FoldSelectorEffectedChannel[A,B] extends FoldSelectorEffectedInput[A,B] with FoldSelectorEffectedOutput[A,B]
{

 override def current: Channel[A]

 val foldSelector: FoldSelect[B]
 val index:        Int


}

object FoldSelectorEffectedChannel
{
  def apply[A,B](foldSelect:FoldSelect[B], index:Int, chFun:()=>Channel[A]):FoldSelectorEffectedChannel[A,B]=
        new CFoldSelectorEffectedChannel(foldSelect,index,chFun)
}

class CFoldSelectorEffectedChannel[A,B](val foldSelector: FoldSelect[B], val index:Int,chFun:()=>Channel[A]) extends FoldSelectorEffectedChannel[A,B]
{
 override val api = chFun().api
 override def current(): Channel[A] = chFun()
}
