package gopher

import cps._
import gopher.impl._
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.TimerTask;
import scala.annotation.unchecked.uncheckedVariance
import scala.util._
import scala.concurrent.duration._
import scala.language.postfixOps
  
import java.util.logging.{Level => LogLevel}


/**
 * Select group is a virtual 'lock' object.
 * Readers and writers are grouped into select groups. When
 * event about avaiability to read or to write is arrived and 
 * no current event group members is running, than run of one of the members
 * is triggered.
 * I.e. only one from group can run.
 *
 * Note, that application develeper usually not work with `SelectGroup` directly, 
 * it is created internally by `select`  pseudostatement.
 * 
 *@see [gopher.Select]
 *@see [gopher.select]
 **/
class SelectGroup[F[_], S](api: Gopher[F])  extends SelectListeners[F,S,S]: 

    thisSelectGroup =>

    /**
     * instance of select group created for call of select.
     * 0 - free
     * 1 - now processes
     * 2 - expired
     **/
    val waitState: AtomicInteger = new AtomicInteger(0)
    private var call: Try[S] => Unit = { _ => () }
    private inline def m = api.asyncMonad
    private val retval = m.adoptCallbackStyle[S](f => call=f)
    private val startTime = new AtomicLong(0L)
    var timeoutScheduled: Option[Time.Scheduled] = None

    override def asyncMonad = api.asyncMonad  
   
    def addReader[A](ch: ReadChannel[F,A], action: Try[A]=>F[S]): Unit =
        val record = ReaderRecord(ch, action)
        ch.addReader(record)

    def addWriter[A](ch: WriteChannel[F,A], element: A, action: Try[Unit]=>F[S]): Unit =
        val record = WriterRecord(ch, element, action)
        ch.addWriter(record)

    def setTimeout(timeout: FiniteDuration, action: Try[FiniteDuration] => F[S]): Unit =
        timeoutScheduled.foreach(_.cancel())
        val record = new TimeoutRecord(timeout,action)
        val newTask = () => {
          val v = System.currentTimeMillis() - startTime.get()
          record.capture() match 
              case Some(f) => f(Success(v milliseconds))
              case None => // do nothing.
        }
        timeoutScheduled = Some(api.time.schedule(newTask,timeout))
        
        

    def step():F[S] =
       retval

    def runAsync():F[S] =
       retval

    transparent inline def apply(inline pf: PartialFunction[Any,S])(using mc: CpsMonadContext[F]): S =
    ${  
        SelectMacro.onceImpl[F,S]('pf, 'api, 'mc )  
    }    
  
    transparent inline def select(inline pf: PartialFunction[Any,S])(using mc: CpsMonadContext[F]): S =
    ${  
        SelectMacro.onceImpl[F,S]('pf, 'api, 'mc )  
    }

    /**
    * short alias for SelectFold.Done
    */
    def done[S](s:S):SelectFold.Done[S] =
      SelectFold.Done(s)

    /**
     * FluentDSL for user SelectGroup without macroses.
     *```
     * SelectGroup.onRead(input){ x => println(x) }
     *            .onRead(endSignal){ () => done=true }
     *```        
     **/
    def  onRead[A](ch: ReadChannel[F,A]) (f: A => S ): this.type =
      addReader[A](ch,{
        case Success(a) => m.tryPure(f(a))
        case Failure(ex) => m.error(ex)
      })
      this


    // reading call will be tranformed to reader_async in async expressions
    def  onReadAsync[A](ch: ReadChannel[F,A])(f: A => F[S] ): this.type =
      addReader[A](ch,{
        case Success(a) => m.tryImpure(f(a))
        case Failure(ex) => m.error(ex) 
      })
      this
    
    // reading call will be tranformed to reader_async in async expressions
    def  onRead_async[A](ch: ReadChannel[F,A])(f: A => F[S] ): F[this.type] =
      m.pure(onReadAsync(ch)(f))

    /**
     * FluentDSL for user SelectGroup without macroses.
     *```
     * SelectGroup.onWrite(input){ x => println(x) }
     *            .onWrite(endSignal){ () => done=true }
     *```        
     **/
    def  onWrite[A](ch: WriteChannel[F,A], a: =>A)(f: A =>S ): this.type =
      addWriter[A](ch,a,{
        case Success(()) => m.tryPure(f(a))
        case Failure(ex) => m.error(ex)
      })
      this


    def  onWriteAsync[A](ch: WriteChannel[F,A], a: ()=>F[A]) (f: A => F[S] ): this.type =
      m.map(a()){ x => 
        addWriter[A](ch,x,{
          case Success(()) => m.tryImpure(f(x))
          case Failure(ex) => m.error(ex)
        })
      }
      this
      

    def onTimeout(t:FiniteDuration)(f: FiniteDuration => S): this.type =
      setTimeout(t,{
         case  Success(x) => m.tryPure(f(x))
         case  Failure(ex) => m.error(ex)
      }) 
      this

    def onTimeoutAsync(t:FiniteDuration)(f: FiniteDuration => F[S]): this.type =
      setTimeout(t,{
          case  Success(x) => m.tryImpure(f(x))
          case  Failure(ex) => m.error(ex)
      })
      this
  

    def onTimeout_async(t:FiniteDuration)(f: FiniteDuration => F[S]): F[this.type] =
      m.pure(onTimeoutAsync(t)(f))


    //
    trait Expiration:
      def canExpire: Boolean = true
      def isExpired: Boolean = waitState.get()==2
      def markUsed(): Unit = waitState.set(2)
      def markFree(): Unit = {
        waitState.set(0)
      }



    case class ReaderRecord[A](ch: ReadChannel[F,A], action: Try[A] => F[S]) extends Reader[A] with Expiration:
      type Element = A
      type State = S

      val ready = Expirable.Capture.Ready[Try[A]=>Unit](v => {
            timeoutScheduled.foreach(_.cancel())
            api.spawnAndLogFail(m.mapTry(action(v))(x => call(x)))
      })

      override def capture(): Expirable.Capture[Try[A]=>Unit] =
             // fast path 
             if waitState.compareAndSet(0,1) then
               ready
             else 
               var retval: Expirable.Capture[Try[A]=>Unit] = Expirable.Capture.Expired
               while {
                 waitState.get() match
                  case 2 => retval = Expirable.Capture.Expired
                          false
                  case 1 => retval = Expirable.Capture.WaitChangeComplete
                          false
                  case 0 => // was just freed
                          if (waitState.compareAndSet(0,1)) then
                            retval = ready
                            false
                          else
                            true
                  case _ => // impossible.
                          throw new IllegalStateException("Imposible state of busy flag")
               } do ()
               retval
                



    case class WriterRecord[A](ch: WriteChannel[F,A], 
                               element: A, 
                               action: Try[Unit] => F[S], 
                               ) extends Writer[A] with Expiration:
      type Element = A
      type State = S

      val ready: Expirable.Capture.Ready[(A,Try[Unit]=>Unit)] =
        Expirable.Capture.Ready((element,
          (v:Try[Unit]) => {
            timeoutScheduled.foreach(_.cancel())
            api.spawnAndLogFail(m.mapTry(action(v))(x=>call(x)))
          }
        ))

      override def capture(): Expirable.Capture[(A,Try[Unit]=>Unit)] =
            if waitState.compareAndSet(0,1) then
              ready
            else
              var retval: Expirable.Capture[(A,Try[Unit]=>Unit)] = Expirable.Capture.Expired
              while{
                waitState.get() match
                  case 2 => false
                  case 1 => retval = Expirable.Capture.WaitChangeComplete
                            false 
                  case 0 => 
                    if (waitState.compareAndSet(0,1)) then
                        retval = ready
                        false
                    else
                        true
              } do ()
              retval


    case class TimeoutRecord(duration: FiniteDuration, 
                             action: Try[FiniteDuration] => F[S],
                             ) extends Expiration:

      def capture(): Option[Try[FiniteDuration] => Unit] =
        if (waitState.compareAndSet(0,1)) then
          Some((v:Try[FiniteDuration]) =>
            api.spawnAndLogFail(m.mapTry(action(v))(x => call(x)))
          )
        else
          None


