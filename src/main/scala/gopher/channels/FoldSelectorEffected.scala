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
 * FoldSelectorInput, generated in our fold statement
 */
trait FoldSelectorEffectedInput[A,B] extends Input[A]
{
  def current: Input[A]
  val foldSelector: FoldSelect[B]
  val index:   Int


  def cbread[C](f: ContRead[A,C] => Option[ContRead.In[A] => Future[Continuated[C]]],ft: FlowTermination[C]): Unit = {
    // ignore f, because cbread called only from match in select fold, since this input is not visible to programmer
    cbreadIfNotActive()
  }


  def refreshReader():Unit = {
    foldSelector.inputIndices.put(current,index)
    cbreadIfNotActive()
  }

  /**
    * Call cbread on current channel with dispath function if current channels is not active
    * (i.e. if we not in wait of other cbread call)
    */
  private def cbreadIfNotActive(): Unit =
  {
    val s = current
    val notSet = foldSelector.activeReaders.get(s).isEmpty
    if (notSet) {
      foldSelector.activeReaders.put(s, true)
      val ft = foldSelector.selector
      s.cbread((cont: ContRead[A, B]) => {
        foldSelector.activeReaders.remove(s)
        foldSelector.dispathReader[A, B](s, ft)
      }, ft)
    }
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
    cbwriterIfNotActive()
  }


  def refreshWriter():Unit =
  {
    foldSelector.outputIndices.put(current,index)
    cbwriterIfNotActive()
  }

  private def cbwriterIfNotActive(): Unit =
  {
    val s = current
    val notSet = foldSelector.activeWriters.get(s).isEmpty
    if (notSet) {
      foldSelector.activeWriters.put(s,true)
      val ft = foldSelector.selector
      s.cbwrite((cw:ContWrite[A,B]) => {
        foldSelector.activeWriters.remove(s)
        foldSelector.dispathWriter(s, ft)
      }, ft)
    }

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
