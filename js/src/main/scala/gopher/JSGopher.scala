package gopher

import cps.*
import java.util.Timer
import java.util.logging.*
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.scalajs.concurrent.*

class JSGopher[F[_]:CpsSchedulingMonad](cfg: JSGopherConfig) extends Gopher[F]:


   def makeChannel[A](bufSize:Int = 0, autoClose: Boolean = false) =
      if (!autoClose) then
         if (bufSize == 0) then
            impl.UnbufferedChannel[F,A](this)
         else 
            impl.BufferedChannel[F,A](this,bufSize)
      else
         impl.PromiseChannel[F,A](this)
      

   val time = new impl.JSTime(this)

   def setLogFun(logFun:(Level, String, Throwable|Null) => Unit): ((Level, String, Throwable|Null) => Unit) =
      val r = currentLogFun
      currentLogFun = logFun
      r 

   def log(level: Level, message: String, ex: Throwable| Null): Unit =
      currentLogFun.apply(level,message,ex)

   def taskExecutionContext: ExecutionContext = JSExecutionContext.queue 

   private var currentLogFun: (Level, String, Throwable|Null )=> Unit = { (level,message,ex) =>
      System.err.println(s"${level}:${message}");
      if !(ex eq null) then
         ex.nn.printStackTrace()
   } 


object JSGopher extends GopherAPI:

   def apply[F[_]:CpsSchedulingMonad](cfg: GopherConfig):Gopher[F] =
      val jsConfig = cfg match
                        case DefaultGopherConfig => JSGopherConfig("default")
                        case jcfg:JSGopherConfig => jcfg
      new JSGopher[F](jsConfig)

   val timer = new Timer("gopher")



val Gopher = JSGopher   

