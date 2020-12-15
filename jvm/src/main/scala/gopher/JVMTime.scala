package gopher

import scala.concurrent.duration._
import scala.util._
import java.util.concurrent.TimeUnit
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class JVMTime[F[_]](gopherAPI: JVMGopher[F]) extends Time[F](gopherAPI) {

  def schedule(fun: () => Unit, delay: FiniteDuration): Time.Scheduled = 
    new JVMScheduled(fun,delay)

  class JVMScheduled(fun: ()=>Unit, delay: FiniteDuration) extends Time.Scheduled  {

      val listeners = new ConcurrentLinkedQueue[Try[Boolean]=>Unit]
      val cancelled = new AtomicBoolean(false)
 
      var wrapper = new Runnable() {
        override def run(): Unit = 
            val doRun = !cancelled.get()
            try {
              if (doRun) {
                  fun()
              }
            } catch {
               case ex: Throwable =>
                // TODO: set log.
                notifyListeners(Failure(ex))
            }            
            notifyListeners(Success(doRun))
      }

      val jf = gopherAPI.scheduledExecutor.schedule(wrapper, delay.toMillis, TimeUnit.MILLISECONDS)
      
      def notifyListeners(value: Try[Boolean]): Unit = 
          while(! listeners.isEmpty) 
            val l = listeners.poll()
            if (! (l eq null)) then
               try
                 l.apply(value)
               catch
                 case ex: Throwable =>
                  // TODO: configure logging
                  ex.printStackTrace()
               
            
      def cancel(): Boolean = 
         cancelled.set(true)
         val r = jf.cancel(false)
         if (r) then
            notifyListeners(Success(false))
         r

      def onDone(listener: Try[Boolean] => Unit): Unit =
        listeners.offer(listener)
      
          

  }

}

