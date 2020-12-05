package gopher

import cps._
import gopher.impl._

import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.ForkJoinPool



class JVMGopher[F[_]:CpsSchedulingMonad](cfg: JVMGopherConfig) extends Gopher[F]:


   def makeChannel[A](bufSize:Int) =
       if (bufSize == 1)
          MTUnbufferedChannel[F,A](cfg.controlExecutor,cfg.taskExecutor)
       else 
          ???

object JVMGopher extends GopherAPI:

   def apply[F[_]:CpsSchedulingMonad](cfg: GopherConfig):Gopher[F] =
      val jvmConfig = cfg match
                        case DefaultGopherConfig => defaultConfig
                        case jcfg:JVMGopherConfig => jcfg
      new JVMGopher[F](jvmConfig)

   lazy val defaultConfig=JVMGopherConfig(
      controlExecutor=Executors.newFixedThreadPool(2),
      taskExecutor=ForkJoinPool.commonPool()
   )

