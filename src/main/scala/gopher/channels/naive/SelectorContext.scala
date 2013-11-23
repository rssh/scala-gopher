package gopher.channels.naive

import java.util.concurrent.{ Future => JFuture, _ }
import scala.concurrent._
import scala.collection.immutable._
import scala.util.control._
import gopher.channels._
import scala.util._
import akka.actor._


/**
 * context, which await of doing one of blocked operations.
 * We can look on one as on implementation of go select statement:
 * we can add to context pairs of inputs and outputs, and then
 * when some of inputs and outputs are non-blockde, than action is performed.
 * I.e. next block of 'go' select:
 * <pre>
 * for(;;) {
 *  select match
 *    case x <- a => f1(x)
 *    case x <- b => f2(x)
 *    case 1 -> c => f3
 *    case _  => f4
 * }
 * </pre>
 *  is mapped do
 * <pre>
 *  slc = new SelectorContext()
 *  slc.addInputAction(a, x=>f1(x))
 *  slc.addInputAction(b, x=>f2(x))
 *  slc.addOutputAction(c, {f4; Some(1) }
 *  slc.addIddleAction(f4)
 *  slc.run()
 * </pre>
 *  (and with help of macroses, can be write as
 * <pre>
 *  selector match {
 *    case x <- a => f1(x)
 *    case x <- b => f2(x)
 *    case 1 -> c => f3
 *     case _ => f4
 *  }
 * </pre>
 */
class SelectorContext(
    val executionContextProvider: ChannelsExecutionContextProvider = DefaultChannelsExecutionContextProvider, 
    val actorSystemProvider: ChannelsActorSystemProvider = DefaultChannelsActorSystemProvider)
                                extends Activable with NaiveTie {

  selectorContextTie =>

  implicit def executionContext: ExecutionContext = executionContextProvider.executionContext
  implicit def actorSystem: ActorSystem = actorSystemProvider.actorSystem
    
  def addReadAction[A](ch: API#IChannel[A], action: ReadAction[A]): this.type =
    {
      ch.addReadListener(this, action)
      inputListeners = action :: inputListeners
      activables = ch :: activables
      this
    }

  /**
   * called before selector context become running.
   */
  def addInputAction[A](channel: NaiveInputChannel[A], action: A => Future[Boolean]): Unit =
    {
      val l: ReadAction[A] = new ReadAction[A] {
        def apply(input: ReadActionInput[A]): Option[Future[ReadActionOutput]] =
          if (enabled) {
            val retval = try {
               val future = action(input.value) map {x => 
                 ReadActionOutput(true)
               }
               future onComplete { x =>
                 latch.countDown()
                 x match {
                   case Failure(ex) => lastException = ex
                   case _ =>
                 }
               }
               Some(future)
            } catch {
              case t: Throwable =>
                lastException = t
                latch.countDown();
                None
            }
            retval
          } else {
            None
          }
      }
      addReadAction(channel, l)
    }

  def addWriteAction[A](ch: API#OChannel[A], action: WriteAction[A]): this.type =
    {
      ch.addWriteListener(this, action)
      outputListeners = action :: outputListeners
      activables = ch :: activables
      this
    }

  def addOutputAction[A](channel: NaiveOutputChannel[A], action: () => Future[Option[A]]): this.type =
    {
      val l = new WriteAction[A] {
        def apply(input: WriteActionInput[A]): Option[Future[WriteActionOutput[A]]] = {
          if (enabled) {
            try {
              val future = action() map(  WriteActionOutput(_,true) )
              future.onComplete{ x =>
                latch.countDown();
                x match {
                  case Failure(t) => lastException = t
                  case Success(_) => 
                }
              }
              Some(future)
            } catch {
              case t: Throwable =>
                lastException = t
                latch.countDown();
                None
            }
          } else { 
            None
          }
        }
      }
      addWriteAction(channel,l)
   }

  def setIdleAction(action:IdleAction): this.type =
  {
    idleAction = Some(action)
    this
  }
  
  def setIdleAction(a: Unit => Unit): Unit =
    {
      val action = new IdleAction{
        def apply(tie: TieJoin): Future[Boolean] =
         Future{ a(); true } 
      }
      setIdleAction(action)
    }

  
  def runOnce(): Unit =
    {
      latch = new CountDownLatch(1)
      lastException = null;
      enabled = true
      activate()
      var toQuit = latch.getCount()>0
      while(!toQuit) {
        enabled=true
        //System.err.println("selectorContext - beofre lanch wait, this="+this)        
        latch.await(IDLE_MILLISECONDS, TimeUnit.MILLISECONDS)
        //System.err.println("selectorContext - after lanch wait, this="+this) 
        enabled = false
        if (lastException != null) {
           throw lastException;
        }
        if (latch.getCount() > 0) {
           idleAction.foreach{ x =>
             latch.countDown()
             x(this)
             toQuit=true;
           } 
        }else{
          toQuit=true
        }
      }    
    }
  
  
    

  def activate(): Unit =
    {
      activables foreach (_.activate)
    }
  
  def start() = {
    System.err.println("tie start");
    activate()
    go
    this
  }

  /**
   * enable listeners and outputChannels
   */
  def go: Future[Unit] =
    {
      executionContext.execute(new Runnable(){
        def run(): Unit = {
          runOnce()
          if (!shutdowned) {
            executionContext.execute(this)
          }
        }
      })
      shutdownPromise.future
    }

  def run: Unit =
    {
      while (!shutdowned) {
        runOnce();
      }
    }
  
  def waitShutdown = run

  def shutdown(): Unit =
    {
      enabled = false
      shutdowned = true
      // allow gc to cleanup listeners.
      inputListeners = Nil
      outputListeners = Nil
      shutdownPromise.complete(Success(()))
    }

  // TODO: revice exception flow
  def processExclusive[A](f: => Future[A],whenLocked: => A): Future[A] = 
  {
    if (latch==null) latch = new CountDownLatch(1);
    //TODO:  think - how to unify countDown and getCount in one operation.
    if (latch.getCount() > 0) {
       latch.countDown();
       try {
         val r = f
         r.onComplete{
           case Success(x) => /* all ok */
           case Failure(ex) => lastException = ex  
         }
         r
       }catch{
         case ex: Exception =>
           lastException = ex
           throw ex
       }
    }else{
      Promise successful whenLocked future
    }
  }
  
  def shutdownFuture: scala.concurrent.Future[Unit] = shutdownPromise.future

  
  
  private var inputListeners: List[ReadAction[_]] = Nil
  private var outputListeners: List[WriteAction[_]] = Nil

  private var activables: List[Activable] = Nil

  @volatile
  private var enabled = false;

  @volatile
  private var shutdowned = false;

  @volatile
  private var latch: CountDownLatch = null;

  @volatile
  private var lastException: Throwable = null;

  private val IDLE_MILLISECONDS = 100;
  private var idleAction: Option[IdleAction] = None; /*IdleAction.doNothing;*/

  private val shutdownPromise = Promise[Unit]()
  
}
