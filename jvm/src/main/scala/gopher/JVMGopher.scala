package gopher

import cps._
import gopher.impl._

import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.ForkJoinPool
import java.util.Timer



class JVMGopher[F[_]:CpsSchedulingMonad](cfg: JVMGopherConfig) extends Gopher[F]:


   def makeChannel[A](bufSize:Int) =
       if (bufSize == 0)
         GuardedSPSCUnbufferedChannel[F,A](this, cfg.controlExecutor,cfg.taskExecutor)
       else 
         GuardedSPSCBufferedChannel[F,A](this, bufSize, cfg.controlExecutor,cfg.taskExecutor) 

   def timer = JVMGopher.timer

   def taskExecutor = cfg.taskExecutor



object JVMGopher extends GopherAPI:

   def apply[F[_]:CpsSchedulingMonad](cfg: GopherConfig):Gopher[F] =
      val jvmConfig = cfg match
                        case DefaultGopherConfig => defaultConfig
                        case jcfg:JVMGopherConfig => jcfg
      new JVMGopher[F](jvmConfig)

   lazy val timer = new Timer("gopher")   

   lazy val defaultConfig=JVMGopherConfig(
      controlExecutor=Executors.newFixedThreadPool(2),
      taskExecutor=ForkJoinPool.commonPool()
   )

