package gopher

import cps._
import java.util.concurrent.atomic.AtomicInteger
import scala.util.Try
  


trait SelectGroup[F[_]:CpsSchedulingMonad, S]:

    /**
     * instance of select group created for call of select.
     **/
    val waitState: AtomicInteger = new AtomicInteger(0)
    var call: Try[S] => Unit = { _ => () }
    private inline def m = summon[CpsSchedulingMonad[F]]
    val retval = m.adoptCallbackStyle[S](f => call=f)


    def addReader[A](ch: ReadChannel[F,A], action: Try[A]=>F[S]): Unit =
        val record = ReaderRecord(ch, action)
        ch.addReader(record)

    def addWriter[A](ch: WriteChannel[F,A], element: A, action: Try[Unit]=>F[S]): Unit =
        val record = WriterRecord(ch, element, action)
        ch.addWriter(record)

    def step():F[S] =
       retval

                 
    trait Expiration:
      def canExpire: Boolean = true
      def isExpired: Boolean = waitState.get()==2
      def markUsed(): Unit = waitState.lazySet(2)
      def markFree(): Unit = waitState.set(0)

    case class ReaderRecord[A](ch: ReadChannel[F,A], action: Try[A] => F[S]) extends Reader[A] with Expiration:
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
                               action: Try[Unit] => F[S], 
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



