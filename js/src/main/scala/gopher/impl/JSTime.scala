package gopher.impl

import gopher._
import scala.concurrent.duration._
import scala.collection.immutable.Queue
import scala.util._

import java.util.TimerTask

class JSTime[F[_]](gopherAPI: JSGopher[F]) extends Time[F](gopherAPI):

  def schedule(fun:()=>Unit, delay: FiniteDuration): Time.Scheduled =
    
    var listeners: Queue[Try[Boolean]=>Unit] = Queue.empty
    var canceled = false

    def notifyListeners(value: Try[Boolean]): Unit = 
      listeners.foreach{ f=> 
        try
          f(value)
        catch
          case ex: Throwable =>
            ex.printStackTrace()
      }
      listeners = Queue.empty
    
    val task =  new TimerTask {
        override def run(): Unit = {
          // TODO:  log exception (?)
          if (!canceled) then
            try
              fun()
            catch
              case ex: Throwable =>  
                notifyListeners(Failure(ex))
          notifyListeners(Success(!canceled))
        }
    }

    JSGopher.timer.schedule(task,delay.toMillis)


    new Time.Scheduled {

      def cancel(): Boolean =
        val r = task.cancel()
        if (r)
          notifyListeners(Success(false))
        r

      def onDone(f: Try[Boolean] => Unit) =
        listeners = listeners.appended(f)

    }





      
      
