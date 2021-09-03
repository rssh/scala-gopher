package gopher.impl

import gopher._

import scala.util._
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference



/**
 * Input, which combine two other inputs.
 *
 * can be created with '|' operator.
 *
 * {{{
 *   val x = read(x|y)
 * }}}
 */
case class OrReadChannel[F[_],A](x: ReadChannel[F,A], y: ReadChannel[F,A]) extends ReadChannel[F,A]:


  val xClosed: AtomicBoolean = new AtomicBoolean(false)
  val yClosed: AtomicBoolean = new AtomicBoolean(false)

  abstract class CommonBase[B](nested: Reader[B]) {
      val inUse = new AtomicReference[ReadChannel[F,A]|Null](null)
      val used = new AtomicBoolean(false)

      def intercept(readFun:Try[B]=>Unit): Try[B] => Unit 

      /**
      * Can be called only insed wrapper fun,
      *  set current inUse be closed, if n
      * precondition: inUse.get !== null
      * return: true, if bith x and y are closed
      **/
      protected def setClosed(): Boolean = {
        if (inUse.get() eq x) then
          if (!xClosed.get()) then
              xClosed.set(true)
          return yClosed.get()
        else 
          if !yClosed.get() then 
            yClosed.set(true)
          return xClosed.get()
      }

      protected def passToNested(v: Try[B], readFun:Try[B]=>Unit) = {
        if (used.get()) then
          nested.markUsed()
        readFun(v)
      }

      protected def passIfClosed(v: Try[B], readFun: Try[B]=>Unit): Unit = {
        if (setClosed()) {
          passToNested(v, readFun)
        } else {
          inUse.set(null)
        }
      }

      def capture(fromChannel: ReadChannel[F,A]): Expirable.Capture[Try[B]=>Unit] =
        if inUse.compareAndSet(null,fromChannel) then
          nested.capture() match
            case Expirable.Capture.Ready(readFun) => Expirable.Capture.Ready(intercept(readFun))
            case Expirable.Capture.WaitChangeComplete => 
                                              inUse.set(null)
                                              Expirable.Capture.WaitChangeComplete
            case Expirable.Capture.Expired => inUse.set(null)
                                              Expirable.Capture.Expired                      
        else
          Expirable.Capture.WaitChangeComplete

      def markFree(fromChannel: ReadChannel[F,A]): Unit =
        if(inUse.get() eq fromChannel) then
          nested.markFree()
          inUse.set(null)
    
      def markUsed(fromChannel: ReadChannel[F,A]): Unit =
        if (inUse.get() eq fromChannel) then
          used.set(true)    

      def isExpired(fromChannel: ReadChannel[F,A]): Boolean =
        nested.isExpired

      def canExpire: Boolean =
        nested.canExpire
    
  }

  class CommonReader(nested: Reader[A]) extends CommonBase[A](nested) {
    
      def intercept(readFun:Try[A]=>Unit): Try[A] => Unit = {
        case r@Success(a) => 
          passToNested(r, readFun)
        case f@Failure(ex) =>
          if (ex.isInstanceOf[ChannelClosedException]) {
             passIfClosed(f, readFun) 
          } else {
             passToNested(f,readFun)
          }
      }
            
  }

  class WrappedReader[B](common: CommonBase[B], owner: ReadChannel[F,A]) extends Reader[B] {

    def capture(): Expirable.Capture[Try[B]=>Unit] =
      common.capture(owner)

    def canExpire: Boolean = common.canExpire

    def isExpired: Boolean = common.isExpired(owner)

    def markFree(): Unit = common.markFree(owner)
  
    def markUsed(): Unit = common.markUsed(owner)
      
  }

  def addReader(reader: Reader[A]): Unit =
    val common = new CommonReader(reader)
    addCommonReader(common,(c,ch)=>ch.addReader(WrappedReader(common,ch)))
    

  class DoneCommonReader(nested: Reader[Unit]) extends CommonBase[Unit](nested):
    
    def intercept(nestedFun: Try[Unit]=>Unit): Try[Unit] => Unit = {
      case r@Success(x) =>
        passIfClosed(r, nestedFun)
      case r@Failure(ex) =>
        passToNested(r, nestedFun)
    }


  def addDoneReader(reader: Reader[Unit]): Unit =
    addCommonReader(new DoneCommonReader(reader), (c,ch) => ch.addDoneReader(WrappedReader(c,ch)))

  // | is left-associative, so (x|y|z|v).gopherApi better be v.api,
  def gopherApi: Gopher[F] = y.gopherApi

  override def toString() = s"(${x}|${y})"
    

  def addCommonReader[C](common:C, addReaderFun: (C, ReadChannel[F,A]) => Unit): Unit =
    var readerAdded = false
    if !xClosed.get() then
      readerAdded = true
      addReaderFun(common,x)
    if !yClosed.get() then
      readerAdded = true
      addReaderFun(common,y)
    // if all closed, than we should add to any, to receive ChannelClosedException
    if !readerAdded then
      addReaderFun(common,y)

