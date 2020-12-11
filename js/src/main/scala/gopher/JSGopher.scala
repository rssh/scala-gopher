package gopher

import cps._
import java.util.Timer

class JSGopher[F[_]:CpsSchedulingMonad](cfg: JSGopherConfig) extends Gopher[F]:


   def makeChannel[A](bufSize:Int) =
       if (bufSize == 1) then
          impl.UnbufferedChannel[F,A](this)
       else 
          impl.BufferedChannel[F,A](this,bufSize)

   def timer = JSGopher.timer


object JSGopher extends GopherAPI:

   def apply[F[_]:CpsSchedulingMonad](cfg: GopherConfig):Gopher[F] =
      val jsConfig = cfg match
                        case DefaultGopherConfig => JSGopherConfig("default")
                        case jcfg:JSGopherConfig => jcfg
      new JSGopher[F](jsConfig)

   val timer = new Timer("gopher")

