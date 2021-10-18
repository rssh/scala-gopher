package gopher

import cps._
import gopher.impl._

import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicReference
import java.util.Timer
import java.util.logging._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._



class JVMGopher[F[_]:CpsSchedulingMonad](cfg: JVMGopherConfig) extends Gopher[F]:


   def makeChannel[A](bufSize:Int = 0, autoClose: Boolean = false) =
      if autoClose then
         PromiseChannel[F,A](this, cfg.taskExecutor)
      else
         if (bufSize == 0)
            GuardedSPSCUnbufferedChannel[F,A](this, cfg.controlExecutor,cfg.taskExecutor)
         else 
            GuardedSPSCBufferedChannel[F,A](this, bufSize, cfg.controlExecutor,cfg.taskExecutor) 
   
            

   val time = new JVMTime(this)

   def setLogFun(logFun:(Level, String, Throwable|Null) => Unit): ((Level, String, Throwable|Null) => Unit) =
      currentLogFun.getAndSet(logFun)

   def log(level: Level, message: String, ex: Throwable| Null): Unit =
      currentLogFun.get().apply(level,message,ex)
          
   lazy val taskExecutionContext = ExecutionContext.fromExecutor(cfg.taskExecutor)

   def scheduledExecutor = JVMGopher.scheduledExecutor

 
   private val currentLogFun: AtomicReference[(Level,String,Throwable|Null)=>Unit]=new AtomicReference(JVMGopher.defaultLogFun) 




object JVMGopher extends GopherAPI:

   def apply[F[_]:CpsSchedulingMonad](cfg: GopherConfig):Gopher[F] =
      val jvmConfig = cfg match
                        case DefaultGopherConfig => defaultConfig
                        case jcfg:JVMGopherConfig => jcfg
      new JVMGopher[F](jvmConfig)
   
   lazy val scheduledExecutor = Executors.newScheduledThreadPool(1) 

   lazy val defaultConfig=JVMGopherConfig(
      controlExecutor=Executors.newFixedThreadPool(2),
      taskExecutor=ForkJoinPool.commonPool(),
   )

   // need for binary compability
   @deprecated("use summon[Gopher].time instead")
   lazy val timer = new Timer("gopher") 

   val logger = Logger.getLogger("JVMGopher")

   def defaultLogFun(level: Level, message:String, ex: Throwable|Null): Unit = 
      if (ex eq null) {
         logger.log(level, message)
      } else {
         logger.log(level, message, ex)
      }
      

   final val MAX_SPINS = 400

val Gopher = JVMGopher
