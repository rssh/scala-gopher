package gopher

import cps._
import gopher.impl._
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.unchecked.uncheckedVariance
import scala.util._
  

/**
 * Select group is a virtual 'lock' object, where only
 * ne fro rieader and writer can exists at the sae time.
 **/
class SelectGroup[F[_]:CpsSchedulingMonad, S](using FlowTermination[S]):

    /**
     * instance of select group created for call of select.
     **/
    val waitState: AtomicInteger = new AtomicInteger(0)
    var call: Try[S] => Unit = { _ => () }
    private inline def m = summon[CpsSchedulingMonad[F]]
    val retval = m.adoptCallbackStyle[S](f => call=f)
   
    def addReader[A](ch: ReadChannel[F,A], action: FlowTermination[S] ?=> Try[A]=>F[S]): Unit =
        val record = ReaderRecord(ch, action)
        ch.addReader(record)

    def addWriter[A](ch: WriteChannel[F,A], element: A, action: FlowTermination[S] ?=> Try[Unit]=>F[S]): Unit =
        val record = WriterRecord(ch, element, action)
        ch.addWriter(record)

    def step():F[S] =
       retval


    /**
     * FluentDSL for user SelectGroup without macroses.
     *```
     * SelectGroup.reading(input){ x => println(x) }
     *            .reading(endSignal){ () => done=true }
     *```        
     **/
    def  reading[A](ch: ReadChannel[F,A]) (f: A => S ): this.type =
      addReader[A](ch,{
        case Success(a) => m.tryPure(f(a))
        case Failure(ex) => m.error(ex)
      })
      this

    // reading call will be tranformed to reader_async in async expressions
    def  reading_async[A](ch: ReadChannel[F,A]) (f: A => F[S] ): F[this.type] =
      addReader[A](ch,{
        case Success(a) => m.tryImpure(f(a))
        case Failure(ex) => m.error(ex) 
      })
      m.pure(this)


    //  TODO: version with flow termination
    //def  reading[A](ch: ReadChannel[F,A])( flowTermination ?=> A => F[S] )
  
    //


                 
    trait Expiration:
      def canExpire: Boolean = true
      def isExpired: Boolean = waitState.get()==2
      def markUsed(): Unit = waitState.lazySet(2)
      def markFree(): Unit = waitState.set(0)

    case class ReaderRecord[A](ch: ReadChannel[F,A], action: FlowTermination[S] ?=> Try[A] => F[S]) extends Reader[A] with Expiration:
      type Element = A
      type State = S

      override def capture(): Option[Try[A]=>Unit] =
             if waitState.compareAndSet(0,1) then
                 Some(v => {
                    m.spawn(
                      m.mapTry(action(v))(x => call(x))
                    )
                 })
             else
                 None



    case class WriterRecord[A](ch: WriteChannel[F,A], 
                               element: A, 
                               action: FlowTermination[S] ?=> Try[Unit] => F[S], 
                               ) extends Writer[A] with Expiration:
      type Element = A
      type State = S

      override def capture(): Option[(A,Try[Unit]=>Unit)] =
             if waitState.compareAndSet(0,1) then
                 Some((element, (v:Try[Unit]) => m.spawn(
                                                   m.mapTry(action(v))(x=>call(x))
                                                 )))
             else
                 None



