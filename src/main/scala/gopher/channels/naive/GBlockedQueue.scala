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
class GBlockedQueue[A: ClassTag](size: Int, 
                                 settedName: String,
                                 override val executionContextProvider: ChannelsExecutionContextProvider, 
                                 override val actorSystemProvider: ChannelsActorSystemProvider,
                                 override val loggerFactory: ChannelsLoggerFactory) 
                                                                   extends InputOutputChannelBase[A] 
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
      readListeners.add((tie, f))
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
    val p = Promise[A]()
    val f = new ReadAction[A] {
      def apply(input: ReadActionInput[A]): Option[Future[ReadActionOutput]] =
      {
        p complete Success(input.value)
        // compiler bug 
        Some(p.future.map(x => ReadActionOutput(continue=false))(thisGBlockedQueue.executionContext))
      }
    }
    addReadListener(internalTie, f)
    p.future
  }
  
  def readAsyncTimeout(timeout: FiniteDuration): Future[Option[A]] = 
  {
    val p = Promise[Option[A]]()
    val f = new ReadAction[A] {
      def apply(input: ReadActionInput[A]): Option[Future[ReadActionOutput]] =
      {
        val notCompleted = if (!p.isCompleted) {
                             try {
                              p complete Success(Some(input.value))
                              true
                             }catch{
                               case ex: IllegalStateException =>
                                   false
                             }
                           } else false
        Some(Promise.successful(
            ReadActionOutput(continue=false)
        ).future)
      }      
    }
    
    actorSystem.scheduler.scheduleOnce(timeout)(
           if (!p.isCompleted) {
             try {
                p complete Success(None)
             }catch{
               case e: IllegalStateException =>
                 /* promise was already completed, do nothing */
             }
           }    
    )
    
    addReadListener(internalTie, f)
    
    p.future
 
  }
  
  
  def readImmediatly: Option[A] =
    optTryDoStepAsync(inTryLock(bufferLock)(readElementBlocked, None))

  // guess that we work in millis resolution.
  override def readBlockedTimeout(timeout: FiniteDuration): Option[A] =
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
      if (logger.isTraceEnabled()) {
        logger.trace(s"addTraceListener ${f} from ${tie} with tag ${tie.tag}")
      }
      writeListeners.add((tie,f))
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

  override def writeBlockedTimeout(x: A, timeout: FiniteDuration): Boolean =
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
      def apply(input: WriteActionInput[A]): Option[Future[WriteActionOutput[A]]] =
      {
        p complete Success(())
        Some(Promise.successful(WriteActionOutput(value=Some(x),continue=false)).future)
      }
    }
    addWriteListener(internalTie, f)
    p.future
  }
  
  def writeAsyncTimeout(x:A, timeout: FiniteDuration): Future[Boolean] =
  {
    val p = Promise[Boolean]()
    val f = new WriteAction[A] {
      override def apply(input: WriteActionInput[A]): Option[Future[WriteActionOutput[A]]] =
      {
        p complete Success(true)
        Some(
            Promise successful WriteActionOutput(value=Some(x),continue=false) future
        )
      }
    }
    addWriteListener(internalTie, f)
    val t = actorSystem.scheduler.scheduleOnce(timeout){
      if (!p.isCompleted) {
        try {
          p.success(false)
        }catch{
          case ex: IllegalStateException =>
        }
      }
    }
    p.future
  }

  def shutdown() {
    shutdowned = true;
    shutdownPromise.complete(Success(shutdowned))
  }
  
  
  /**
   * pass all output, which can be readed from this channel, to given actor.
   */
  def bindRead(tie: NaiveTie, actor: ActorRef): Unit = 
  {
    // TODO: will be garbage-collected. do somehting with this.
    addReadListener(tie,
       new ReadAction[A] {
        override def apply(input: ReadActionInput[A]): Option[Future[ReadActionOutput]] =
           { actor ! input.value; 
             Some(Promise successful ReadActionOutput(true) future) 
           }
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
    inTryLock(doStepLock)(doStepAsync(), 
        if (logger.isTraceEnabled()) {
          logger.trace(s"tryDoStepAsync for $this: stepLock is blocked.")
        }
    )

  def activate() = {
    if (logger.isTraceEnabled()) {
      logger.trace(s"activate $this, tag=$tag")
    }
    tryDoStepAsync  
  }
    
  /**
   * Run chunk of queue event loop inside thread, specified by
   * execution context, passed in channel initializer;
   *
   */
  def doStepAsync(): Unit = {
    //implicit val ec = executionContext;
    if (logger.isTraceEnabled()) {
        logger.trace("doStepAsyn - planning step")
    }
    executionContext.execute(new Runnable(){
      def run() {
        val toContinue = inTryLock(doStepLock)( 
                            { doStep() }, 
                            {
                              if (logger.isTraceEnabled()) {
                                 logger.trace("in doStepAsync - doStep is locked, leaving")
                              }
                              false;
                            }
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
  private def doStep(maxN: Int = 100): Boolean =
  {   
    inLock(bufferLock) { 
     inLock(doStepLock) {
      if (logger.isTraceEnabled()) {
         logger.trace(s"doStep - after locks, count=${count} size=${size}")
      } 
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
  }

  private def fireNewSpaceBlocked: Boolean =
    {
      var done = false;
      val toAdd = new JLinkedList[(NaiveTie,WriteAction[A])]()
      while(!writeListeners.isEmpty() && !done) {
        var h = writeListeners.poll()
        if (logger.isTraceEnabled()) {
           logger.trace("poll writeListeners, h="+h)
        }
        if (h!=null) {
             val writeListener = h._2
             var input = WriteActionInput(h._1.writeJoin(this),this)
             writeListener(input) match {
               case None => toAdd.add(h)
               case Some(outputFuture) =>
                if (outputFuture.isCompleted) {
                   val output = Await.result(outputFuture, Duration.Zero)
                   if (logger.isTraceEnabled()) {
                      logger.trace(s"write:complete, output.value=${output.value} count=${count}")
                   }
                   val norec = output.value match {
                     case None => true
                     case Some(x) => writeElementBlocked(x)
                   }
                   if (norec) {
                     if (output.continue) {
                        writeListeners.add(h)
                     }
                   } else {
                     // x was not writed becouse we have no free place now.
                     // so, add writeListener back
                     val finalAction = new WriteAction[A]() {
                        override def apply(in:WriteActionInput[A]):Option[Future[WriteActionOutput[A]]] = 
                           Some(outputFuture)   
                     }
                     if (logger.isTraceEnabled()) {
                       logger.trace("resubmitting write")
                     }
                     this.addWriteListener(h._1,finalAction)
                   }
                } else {
                   outputFuture.onComplete{ output =>
                       //i.e. call me again when I will be completed.
                       val finalAction = new WriteAction[A]() {
                          override def apply(in:WriteActionInput[A]):Option[Future[WriteActionOutput[A]]] = 
                          {
                            output match {
                              case Failure(f) => Some(outputFuture)
                              case Success(x) =>
                                if (x.continue) {
                                  // put original action in queue instead final 
                                  addWriteListener(h._1,h._2)
                                }
                                // Promise.true
                                Some(Promise successful WriteActionOutput[A](x.value,false) future)
                            }
                          }
                       }
                       if (logger.isTraceEnabled()) {
                         logger.trace("resubmitting uncomplete write")
                       }
                       this.addWriteListener(h._1,finalAction)
                       tryDoStepAsync
                   }(executionContext)
                }
                done = true
             }             
        }
      }
      writeListeners.addAll(toAdd)
      done
    }

  
  private def fireNewElementBlocked: Boolean =
    {
      var done = false
      val toAdd = new JLinkedList[(NaiveTie,ReadAction[A])]()
      while(!readListeners.isEmpty() && !done) {
         var h = readListeners.poll();
         if (h!=null) {
           val readListener = h._2
           val elementToRead = buffer(readIndex)
           val input = ReadActionInput(
                              h._1.readJoin(this),this,elementToRead)
           readListener(input) match {
               case Some(outputFuture) =>
                 freeElementBlocked
                 done = true
                 outputFuture.foreach{ output =>
                   if (output.continue) {
                      readListeners.add(h)
                      tryDoStepAsync
                   }
                 }(executionContext)
               case None => 
                   toAdd.add(h)
           }          
         }
      }
      readListeners.addAll(toAdd)
      done
    }

  private def freeElementBlocked =
    {
      if (logger.isTraceEnabled()) {
        logger.trace(s"free element blocked for ${this}");
      }
      buffer(readIndex) = emptyA
      readIndex = ((readIndex + 1) % size)
      count -= 1
    }

  private def readElementBlocked: Option[A] =
    {
      if (logger.isTraceEnabled()) {
           logger.trace(s"readElementBlocked")
      }
      if (count > 0) {
        val retval = buffer(readIndex)
        freeElementBlocked
        Some(retval)
      } else None
    }

  private def writeElementBlocked(a: A): Boolean =
    {
     val r = if (count < size) {
        buffer(writeIndex) = a
        writeIndex = ((writeIndex + 1) % size)
        count += 1
        true
      } else {
        false
      }
      if (logger.isTraceEnabled()) {
           logger.trace(s"writeElementBlocked ${a} ${r}")
      }
      r
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
  private val shutdownPromise: Promise[Unit] = Promise();
  
 
  private[this] val internalTie = new NaiveTie() {
    
     def addReadAction[B](ch: API#IChannel[B], action: ReadAction[B]): this.type =
     {
       if (! (ch eq thisGBlockedQueue)) {
         throw new IllegalArgumentException("internal tie accept tasks only for this channel")
       }
       thisGBlockedQueue.addReadListener(this, action.asInstanceOf[ReadAction[A]])
       this
     }
  
     def addWriteAction[B](ch: API#OChannel[B], action: WriteAction[B])  =
     {
       if (! (ch eq thisGBlockedQueue)) {
         throw new IllegalArgumentException("internal tie accept tasks only for this channel")
       }
       thisGBlockedQueue.addWriteListener(this, action.asInstanceOf[WriteAction[A]])
       this
     }
  
     def setIdleAction(action: IdleAction) =
     {
       throw new IllegalArgumentException("IdleAction is not applicable for GBlockedQueue Tie")
     }
    
     def start() = {
       thisGBlockedQueue.activate();
       this
     }
  
  
     def shutdown() {
       thisGBlockedQueue.shutdown();
     }
  
     /**
      * Wait shutdowm.  Can utilize current thread for message processing.
      */
     def waitShutdown() = ???

     def processExclusive[A](f: => Future[A],whenLocked: => A): Future[A] = f
     
     def shutdownFuture: scala.concurrent.Future[Unit] = shutdownPromise.future
    
     def executionContext: ExecutionContext = thisGBlockedQueue.executionContext

     def actorSystem: ActorSystem = thisGBlockedQueue.actorSystem   
     
     def channelsLoggerFactory = thisGBlockedQueue.loggerFactory
     
     def logger  = thisGBlockedQueue.logger

     def tag: String = thisGBlockedQueue.tag
     
  }

  override protected def tag: String = Option(settedName).getOrElse(this.toString());
  
  private[this] val bufferLock = new ReentrantLock();
  private[this] val doStepLock = new ReentrantLock();
  private[this] val readPossibleCondition = bufferLock.newCondition
  private[this] val writePossibleCondition = bufferLock.newCondition

  private val readListeners: ConcurrentLinkedQueue[(NaiveTie,ReadAction[A])] 
                          = new ConcurrentLinkedQueue();
  
  private val writeListeners: ConcurrentLinkedQueue[(NaiveTie,WriteAction[A])] 
                          = new ConcurrentLinkedQueue();
  
  
  
  private[this] implicit val executionContext = executionContextProvider.executionContext;
  private[this] val actorSystem = actorSystemProvider.actorSystem;
  
  override def api = NaiveChannelsAPI.instance
  override def logger = loggerFactory.logger(classOf[GBlockedQueue[A]], tag) 
  
  private[this] final var emptyA: A = _

}
