package gopher.channels

import scala.concurrent._
import akka.actor._
import gopher.util._

trait TieBuilder[API <: ChannelsAPI[API]] extends JLockHelper  {
  
  val api: API
  
  
  def condReading[A](ch: API#IChannel[A])(f: A => Boolean ): this.type =
  {
    val actionFun = () => (ch, readFun2Action(f))
    reads = actionFun::reads 
    this
  }
  
  // TODO: insert async here.
  def reading[A](ch: API#IChannel[A])(f: A => Unit ): this.type =
    condReading[A](ch){a => f(a); true}
   
  
  def condWriting[A](ch: API#OChannel[A])(f: Unit => Option[A]): this.type =
  {
    val actionFun = () => (ch, writeFun2Action(f))
    writes = actionFun::writes
    this
  }
  
  // TODO: insert async here.
  def writing[A](ch: API#OChannel[A])(f: => A): this.type =
    condWriting(ch){ unit => Some(f) }
      
 
  def exclusive: this.type =
  {
    exclusive_ = true
    this
  }
  
  def inclusive: this.type =
  {
    exclusive_ = false
    this
  }

  def |> = andThen
  def `then` = andThen
  
  def andThen: StartTieBuilder[API] =
  {
    val retval = new StartTieBuilder(api,Some(this)) 
    optAfter = Some(retval)
    retval
  }
  
  
  private[channels] var reads: List[() => API#ReadActionRecord[_]] = Nil
  private[channels] var readActions: List[API#ReadActionRecord[_]] = Nil
  
  private[channels] var writes: List[() => API#WriteActionRecord[_]] = Nil
  private[channels] var writeActions: List[API#WriteActionRecord[_]] = Nil
  
  protected var exclusive_ : Boolean = true;
  protected var optAfter: Option[StartTieBuilder[API]] = None
  protected var optBefore: Option[TieBuilder[API]] = None
  
  protected def readFun2Action[A](f: A => Boolean ): ReadAction[A]
  
  protected def writeFun2Action[A](f: Unit => Option[A]): WriteAction[A]
  
  
  
  private[channels] def copyInternals(parent: TieBuilder[API])=
  {
   reads = parent.reads
   writes = parent.writes
   readActions = parent.readActions
   writeActions = parent.writeActions
   exclusive_ = parent.exclusive_
   optAfter = parent.optAfter
   optBefore = parent.optBefore
  }
  
   
  def go(implicit ec: ExecutionContext, as: ActorSystem): Future[Unit] =
  {
   // TODO: hand;e before.
    val tie = api.makeRealTie
    for(r <- reads) {
      val ra = r();
      tie.addReadAction(ra._1,ra._2.asInstanceOf[ReadAction[Any]])
    }
    for(ra <- readActions) {
      tie.addReadAction(ra._1, ra._2.asInstanceOf[ReadAction[Any]])
    }
    for(w <- writes) {
      val wa = w()
      tie.addWriteAction(wa._1.asInstanceOf[API#OChannel[Any]], wa._2)
    }
    for(wa <- writeActions) {
      tie.addWriteAction(wa._1.asInstanceOf[API#OChannel[Any]], wa._2)
    }
    tie.start
    tie.shutdownFuture
  }
  
  
  
}


class StartTieBuilder[API <: ChannelsAPI[API]](val api: API, 
                                               iniOptBefore: Option[TieBuilder[API]]=None)  extends TieBuilder[API] 
{
 
  optBefore=iniOptBefore
  
  def forever: ForeverTieBuilder[API] =
             new ForeverTieBuilder(this)

  def once: OnceTieBuilder[API] = new OnceTieBuilder(this)
  
  def zip[A,B](it: Iterable[A],ch: API#IChannel[B])(f: (A,B) => Boolean ): ZippedTieBuilder[API,A,B] = 
    new ZippedTieBuilder(this, it, ch, f)
 
      
  def write[A](ch: API#OChannel[A])(it:Iterable[A]): WriteTieBuilder[API,A] =  
     new WriteTieBuilder(this, it, ch)
  
  
  def actions: ActionsTieBuilder[API]
   = new ActionsTieBuilder(this)
  
  override def go(implicit ec: ExecutionContext, as: ActorSystem): Future[Unit] =
    new ForeverTieBuilder(this).go
   
  
  protected def readFun2Action[A](f: A => Boolean ): ReadAction[A] =
    throw new IllegalArgumentException("Impossible: readFin2Action can't be called in startTieBuilder")
  
  protected def writeFun2Action[A](f: Unit => Option[A]): WriteAction[A] =
   throw new IllegalArgumentException("Impossible: readFin2Action can't be called in startTieBuilder")
  
}


class ForeverTieBuilder[API <: ChannelsAPI[API]](parent: TieBuilder[API]) extends {val api = parent.api } with TieBuilder[API]
{
   copyInternals(parent)
 
   protected def readFun2Action[A](f: A => Boolean ): ReadAction[A] =
     new ReadAction[A] {
       def apply(in: ReadActionInput[A]): ReadActionOutput =
         ReadActionOutput(readed=in.tie.processExclusive(f(in.value),false), continue=true)
   }
  
  protected def writeFun2Action[A](f: Unit => Option[A]): WriteAction[A] =
     new WriteAction[A] {
       def apply(in: WriteActionInput[A]): WriteActionOutput[A] =
              WriteActionOutput(writed=in.tie.processExclusive(f(), None), continue=true)
    
     }
    
   
   
}


class OnceTieBuilder[API <: ChannelsAPI[API]](parent: TieBuilder[API]) extends TieBuilder[API]
{
  val api = parent.api
  copyInternals(parent)
  
   protected def readFun2Action[A](f: A => Boolean ): ReadAction[A] =
     new ReadAction[A] {
       def apply(in: ReadActionInput[A]): ReadActionOutput =
         ReadActionOutput(readed=in.tie.processExclusive(
                                    {val retval = f(in.value); if (retval) in.tie.shutdown(); retval},
                                    false), 
                          continue=false)
   }
  
  protected def writeFun2Action[A](f: Unit => Option[A]): WriteAction[A] =
     new WriteAction[A] {
       def apply(in: WriteActionInput[A]): WriteActionOutput[A] =
              WriteActionOutput(writed=in.tie.processExclusive(
                                            {val retval = f(); retval.foreach(x => in.tie.shutdown); retval}, 
                                            None), 
                                continue=false)
     }
    
  
  
}

class ZippedTieBuilder[API <: ChannelsAPI[API], A, B](parent: TieBuilder[API],
                                                 collection: Iterable[A], 
                                                 ch: API#IChannel[B],
                                                 f: (A,B) => Boolean ) extends ForeverTieBuilder[API](parent)
{

  class IteratorReadAction(it:Iterator[A]) extends ReadAction[B]
  {
    
    def apply(in: ReadActionInput[B]): ReadActionOutput =
    {
      if (current.isEmpty) {
        if (it.hasNext) {
          process(in,it.next)
        } else {
          in.tie.shutdown
          ReadActionOutput(readed=false, continue=false)
        }
      } else {
        process(in, it.next)
      }
    }
    
    def process(in: ReadActionInput[B], a: A): ReadActionOutput = {
       val readed = in.tie.processExclusive(f(a,in.value), false)
       val (continue, current) = if (readed) {
                                    (if (it.hasNext) 
                                       true 
                                     else 
                                       {in.tie.shutdown; false }, 
                                     None)
                                 } else {
                                    (true, Some(a))
                                 }
       ReadActionOutput(readed,continue)
    }
    
    private[this] var current: Option[A] = None
    
  }
  
  readActions = (ch,new IteratorReadAction(collection.iterator)) :: readActions  

}

class WriteTieBuilder[API <: ChannelsAPI[API],A](parent: TieBuilder[API], collection: Iterable[A], ch: API#OChannel[A]) 
                                                        extends ForeverTieBuilder[API](parent)
{
  
  class IteratorWriteAction(it:Iterator[A]) extends WriteAction[A]
  {
    def apply(in: WriteActionInput[A]): WriteActionOutput[A] =
    {
      if (!it.hasNext) {
        in.tie.shutdown
        WriteActionOutput[A](writed=None, continue=false)
      } else {
        WriteActionOutput[A](writed=in.tie.processExclusive( Some(it.next), None),
                             continue=true)
      }
    }
  }
  
  writeActions = (ch, new IteratorWriteAction(collection.iterator))::writeActions
  
}
                                                
class ActionsTieBuilder[API <: ChannelsAPI[API]](parent: TieBuilder[API]) extends ForeverTieBuilder[API](parent)
{
  
  def onRead[A](ch: API#IChannel[A],a: ReadAction[A]): TieBuilder[API] = 
  {
    readActions = (ch, a)::readActions
    this
  }
  
  def onRead[A](ch: API#IChannel[A])(fa: ReadActionInput[A] => ReadActionOutput): TieBuilder[API] =
  {
    val ra = new ReadAction[A] {
      def apply(in: ReadActionInput[A]): ReadActionOutput = fa(in)
    }
    onRead(ch,ra)
  }

  def onWrite[A](ch: API#OChannel[A],a: WriteAction[A]): ActionsTieBuilder[API] =
  {
    writeActions = (ch, a)::writeActions
    this
  }
  
  def onWrite[A](ch: api.OChannel[A])(fa: WriteActionInput[A] => WriteActionOutput[A]): ActionsTieBuilder[API] =
  {
    val wa = new WriteAction[A] {
      def apply(in: WriteActionInput[A]): WriteActionOutput[A] = fa(in)
    }
    onWrite(ch,wa)
  }
  
  
}
