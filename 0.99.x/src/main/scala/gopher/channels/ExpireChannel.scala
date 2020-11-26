package gopher.channels

import gopher.{FlowTermination, GopherAPI}

import scala.language.postfixOps
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}



class ExpireChannel[A](expire: FiniteDuration, capacity:Int, gopherApi: GopherAPI) extends Channel[A] {

    case class Element(value:A, readed:Promise[Boolean], time: Long)

    val internal = api.makeChannel[Element](capacity)


    /**
      * apply f, when input will be ready and send result to API processor
      */
    override def cbread[B](f: (ContRead[A, B]) => Option[(ContRead.In[A]) => Future[Continuated[B]]], ft: FlowTermination[B]): Unit =
      {

          def fw(cr: ContRead[Element,B]):Option[ContRead.In[Element] => Future[Continuated[B]]] = {
              f(ContRead(f,this,ft)) map { g => {
                  case ContRead.Value(e) =>
                          if (e.readed.trySuccess(true)) {
                              if (System.currentTimeMillis() - expire.toMillis > e.time) {
                                  g(ContRead.Skip)
                              } else {
                                  g(ContRead.Value(e.value))
                              }
                          }  else {
                              g(ContRead.Skip)
                          }
                  case ContRead.Skip => g(ContRead.Skip)
                  case ContRead.ChannelClosed => g(ContRead.ChannelClosed)
                  case ContRead.Failure(ex) => g(ContRead.Failure(ex))
                }
              }
          }
          internal.cbread[B](fw,ft)
      }

    /**
      * apply f and send result to channels processor.
      */
    override def cbwrite[B](f: (ContWrite[A, B]) => Option[(A, Future[Continuated[B]])], ft: FlowTermination[B]): Unit =
    {
        val p = Promise[Boolean]
        implicit val ec = api.gopherExecutionContext
        api.actorSystem.scheduler.scheduleOnce(expire){
            if (p.trySuccess(true)) {
                val e = internal.aread
                // skip one.
            }
        }
        def fw(cw:ContWrite[Element,B]):Option[(Element,Future[Continuated[B]])]=
        {
          f(ContWrite(f,this,ft)) map { case (a,n) =>
              (Element(a,p,System.currentTimeMillis()),n)
          }
        }
        internal.cbwrite(fw,ft)
    }

    override val done: Input[Unit] = internal.done

    override def api: GopherAPI = gopherApi

    override def close(): Unit = internal.close()


}

object ExpireChannel
{

    def apply[A](expireTime:FiniteDuration,capacity:Int)(implicit api:GopherAPI):ExpireChannel[A] =
          new ExpireChannel[A](expireTime,capacity,api)

}