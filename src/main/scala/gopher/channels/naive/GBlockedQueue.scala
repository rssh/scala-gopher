package gopher.channels.naive

import java.util.concurrent.{Future=>JFuture,_}
import java.util.concurrent.locks._
import java.util.{LinkedList => JLinkedList}
import java.lang.ref._
import scala.concurrent.duration._
import scala.reflect._
import scala.concurrent._
import scala.util._
import gopher.channels._
import gopher.util.JLockHelper
import akka.actor._

/**
 * classical blocked queue, which supports listeners.
 */
class GBlockedQueue[A: ClassTag](size: Int, ec: ExecutionContext) extends InputOutputChannel[A] 
                                                                   with NaiveInputChannel[A]
                                                                   with NaiveOutputChannel[A]
                                                                  with JLockHelper
{
                  
  thisGBlockedQueue =>

  /**
   * called, when we want to deque object to readed.
   * If listener accepts read, it returns true with given object.
   * Queue holds weak referencde to listener, so we stop sending
   * message to one, when listener is finalized.
   */
  def addReadListener(tie: NaiveTie, f: ReadAction[A]): Unit =
    {
      readListeners.add((tie,new WeakReference(f)))
      tryDoStepAsync();
    }

 // def addListener(f: A => Boolean): Unit = addReadListener(f)

  def readBlocked: A =
    {
    // TODO: think about emulation 'go' policy
      if (shutdowned) {
        throw new IllegalStateException("quue is shutdowned")
      }
      
      val retval = inLock(bufferLock) {
        while (count == 0) {
          // TODO:  wrap in blocked to help fork0java cle
          readPossibleCondition.await()
        }
        val retval = buffer(readIndex)
        freeElementBlocked
        retval
      }
      tryDoStepAsync();
      retval
    }

  def readAsync: Future[A] = 
  {
    implicit val ec = executionContext
    val p = Promise[A]()
    val f = new ReadAction[A] {
      def apply(input: ReadActionInput[A]): ReadActionOutput =
      {
        p complete Success(input.value)
        ReadActionOutput(readed=true,continue=false)
      }
    }
    addReadListener(internalTie, f)
    p.future
  }
  
  def readAsyncTimeout(timeout: Duration): Future[Option[A]] = 
  {
    //TODO: use akka scheduler 
    implicit val ec = executionContext
    Future({
      // TODO: rewrite.
      readBlockedTimeout(timeout)
    })
  }
  
  
  def readImmediatly: Option[A] =
    optTryDoStepAsync(inTryLock(bufferLock)(readElementBlocked, None))

  // guess that we work in millis resolution.
  def readBlockedTimeout(timeout: Duration): Option[A] =
    optTryDoStepAsync {
      val endOfLock = System.currentTimeMillis() + timeout.unit.toMillis(timeout.length)
      inTryLock(bufferLock, timeout)({
        var millisToLeft = endOfLock - System.currentTimeMillis()
        while (count == 0 && millisToLeft > 0) {
          readPossibleCondition.await(millisToLeft, TimeUnit.MILLISECONDS)
          millisToLeft = endOfLock - System.currentTimeMillis()
        }
        readElementBlocked
      }, None)
    }

  def addWriteListener(tie: NaiveTie, f: WriteAction[A]): Unit =
  {
      writeListeners.add((tie,new WeakReference(f)))
      tryDoStepAsync()
  }

  def writeBlocked(x: A): Unit =
    {
      val retval = inLock(bufferLock) {
        var writed = false
        while (!writed) {
          while (count == size) {
            writePossibleCondition.await()
          }
          writed = writeElementBlocked(x)
        }
        readPossibleCondition.signal()
      }
      tryDoStepAsync
      retval
    }

  def writeImmediatly(x: A): Boolean =
    condTryDoStepAsync(
      inTryLock(bufferLock)(
        writeElementBlocked(x), false))

  def writeBlockedTimeout(x: A, timeout: Duration): Boolean =
    condTryDoStepAsync {
      val endOfLock = System.currentTimeMillis() + timeout.unit.toMillis(timeout.length)
      inTryLock(bufferLock, timeout)({
        var millisToLeft = endOfLock - System.currentTimeMillis()
        while (count == size && millisToLeft > 0) {
          writePossibleCondition.await(millisToLeft, TimeUnit.MILLISECONDS)
          millisToLeft = endOfLock - System.currentTimeMillis()
        }
        writeElementBlocked(x)
      }, false)
    }
  
  def writeAsync(x:A): Future[Unit] =
  {
    val p = Promise[Unit]()
    val f = new WriteAction[A] {
      def apply(input: WriteActionInput[A]): WriteActionOutput[A] =
      {
        p complete Success(())
        WriteActionOutput(writed=Some(x),continue=false)
      }
    }
    addWriteListener(internalTie, f)
    p.future
  }

  def shutdown() {
    shutdowned = true;
  }
  
  
  /**
   * pass all output, which can be readed from this channel, to given actor.
   */
  def bindRead(tie: NaiveTie, actor: ActorRef): Unit = 
  {
    // TODO: will be garbage-collected. do somehting with this.
    addReadListener(tie,
       new ReadAction[A] {
        def apply(input: ReadActionInput[A]): ReadActionOutput =
           { actor ! input.value; ReadActionOutput(true, true) }
      }       
    )
  }
  
  def bindWrite(name: String)(implicit as: ActorSystem): ActorRef =
  {
    FromActorToChannel.create(this, name);
  }
  
  

  @inline
  private[this] def condTryDoStepAsync(x: Boolean): Boolean =
    {
      if (x) tryDoStepAsync()
      x
    }

  @inline
  private[this] def optTryDoStepAsync[T](x: Option[T]) =
    {
      if (x.isDefined) {
        tryDoStepAsync()
      }
      x
    }

  private[this] def tryDoStepAsync() =
    inTryLock(doStepLock)(doStepAsync(), ())

  def activate() = tryDoStepAsync  
    
  /**
   * Run chunk of queue event loop inside thread, specified by
   * execution context, passed in channel initializer;
   *
   */
  def doStepAsync(): Unit = {
    //implicit val ec = executionContext;
    executionContext.execute(new Runnable(){
      def run() {
        val toContinue = inTryLock(doStepLock)( 
                            { doStep() }, false 
                          );
        if (toContinue && ! shutdowned) {
           doStepAsync()
        }
      }
    });
  }

  /**
   * Run chunk of queue event loop inside current thread.
   *
   */
  private def doStep(maxN: Int = 1000): Boolean =
    inLock(bufferLock) {
     inLock(doStepLock) {
      var toContinue = true
      var wasContinue = false;
      var n = maxN;
      val prevCount = count;
      while (toContinue) {
        val readAction = (count > 0 && fireNewElementBlocked)
        val writeAction = (count < size && fireNewSpaceBlocked)
        if (prevCount == size && count < size) {
          writePossibleCondition.signal()
        } else if (prevCount == 0 && count > 0) {
          readPossibleCondition.signal()
        }
        wasContinue = (readAction || writeAction);
        toContinue ||= wasContinue
        n = n - 1
        toContinue &&= (n > 0)
      }
      wasContinue
     } 
    }

  private def fireNewSpaceBlocked: Boolean =
    {
      var done = false;
      var nexts: JLinkedList[(NaiveTie,WeakReference[WriteAction[A]])] = new JLinkedList();
      var nNulls = 0
      while(!writeListeners.isEmpty() && !done) {
        var h = writeListeners.poll()
        if (h!=null) {
           val writeListener = h._2.get
           if (!(writeListener eq null)) {
             var input = WriteActionInput(h._1,this)
             var output = writeListener(input)
             if (output.continue || !output.writed.isDefined) {
               nexts.add(h)
             }
             output.writed foreach { a =>
               writeElementBlocked(a)
               done = true;
             }
           } else {
              nNulls = nNulls + 1
           }
        }
      }
      writeListeners.addAll(nexts);
     // if (nNulls > 0) {
     //   System.err.println("witeActions, nNulls:"+nNulls);
     // }
            
      done
    }

  private def fireNewElementBlocked: Boolean =
    {
      var nexts: JLinkedList[(NaiveTie,WeakReference[ReadAction[A]])] = new JLinkedList();
      var done = false
      var nNulls = 0
      while(!readListeners.isEmpty() && !done) {
         var h = readListeners.poll();
         if (h!=null) {
           val readListener = h._2.get
           if (!(readListener eq null)) {
             val input = ReadActionInput(h._1,this,buffer(readIndex))
             val output = readListener(input)
             if (output.continue || !output.readed) {
                nexts.add(h)
             }
             if (output.readed) {
               freeElementBlocked
               done = true
             }
           } else {
             nNulls = nNulls + 1
           }
         }
      }
      readListeners.addAll(nexts);
      done
    }

  private def freeElementBlocked =
    {
      buffer(readIndex) = emptyA
      readIndex = ((readIndex + 1) % size)
      count -= 1
    }

  private def readElementBlocked: Option[A] =
    {
      if (count > 0) {
        val retval = buffer(readIndex)
        freeElementBlocked
        Some(retval)
      } else None
    }

  private def writeElementBlocked(a: A): Boolean =
    {
      if (count < size) {
        buffer(writeIndex) = a
        writeIndex = ((writeIndex + 1) % size)
        count += 1
        true
      } else {
        false
      }
    }



  private[this] val buffer: Array[A] = new Array[A](size)

  @volatile
  private[this] var readIndex: Int = 0;
  @volatile
  private[this] var writeIndex: Int = 0;
  @volatile
  private[this] var count: Int = 0;
  @volatile
  private[this] var shutdowned: Boolean = false;
  
 
  private[this] val internalTie = new NaiveTie() {
  
     def addReadAction[B](ch: api.IChannel[B], action: ReadAction[B]): Unit =
     {
       if (! (ch eq thisGBlockedQueue)) {
         throw new IllegalArgumentException("internal tie accept tasks only for this channel")
       }
       thisGBlockedQueue.addReadListener(this, action.asInstanceOf[ReadAction[A]])
     }
  
     def addWriteAction[B](ch: api.OChannel[B], action: WriteAction[B]): Unit =
     {
       if (! (ch eq thisGBlockedQueue)) {
         throw new IllegalArgumentException("internal tie accept tasks only for this channel")
       }
       thisGBlockedQueue.addWriteListener(this, action.asInstanceOf[WriteAction[A]])      
     }
  
     def setIdleAction(action: IdleAction): Unit =
     {
       throw new IllegalArgumentException("IdleAction is not applicable for GBlockedQueue Tie")
     }
    
     def start() {
       thisGBlockedQueue.activate();
     }
  
  
     def shutdown() {
       thisGBlockedQueue.shutdown();
     }
  
     /**
      * Wait shutdowm.  Can utilize current thread for message processing.
      */
     def waitShutdown() = ???

   
    
  }
  
  
  private[this] val bufferLock = new ReentrantLock();
  private[this] val doStepLock = new ReentrantLock();
  private[this] val readPossibleCondition = bufferLock.newCondition
  private[this] val writePossibleCondition = bufferLock.newCondition

  private val readListeners: ConcurrentLinkedQueue[(NaiveTie,WeakReference[ReadAction[A]])] 
                          = new ConcurrentLinkedQueue();
  
  private val writeListeners: ConcurrentLinkedQueue[(NaiveTie,WeakReference[WriteAction[A]])] 
                          = new ConcurrentLinkedQueue();
  
  private[this] val executionContext: ExecutionContext = ec;

  private[this] final var emptyA: A = _

}
