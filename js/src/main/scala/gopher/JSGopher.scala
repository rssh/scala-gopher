package gopher

import cps._
import java.util.Timer
import scala.concurrent.duration._

class JSGopher[F[_]:CpsSchedulingMonad](cfg: JSGopherConfig) extends Gopher[F]:


   def makeChannel[A](bufSize:Int = 0, autoClose: Boolean = false, expire: Duration = Duration.Inf) =
      if (expire == Duration.Inf )
         if (!autoClose) then
            if (bufSize == 0) then
               impl.UnbufferedChannel[F,A](this)
            else 
               impl.BufferedChannel[F,A](this,bufSize)
         else
            ???
            //impl.PromiseChannel[F,A](this)
      else
         ???

   def timer = JSGopher.timer


object JSGopher extends GopherAPI:

   def apply[F[_]:CpsSchedulingMonad](cfg: GopherConfig):Gopher[F] =
      val jsConfig = cfg match
                        case DefaultGopherConfig => JSGopherConfig("default")
                        case jcfg:JSGopherConfig => jcfg
      new JSGopher[F](jsConfig)

   val timer = new Timer("gopher")

