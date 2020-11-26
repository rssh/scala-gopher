package gopher.channels


import scala.concurrent._
import scala.concurrent.duration._
import gopher._

/**
 * lazy channel, which created during first input/output operations.
 * (used in transputers as default value for In/Out Ports)
 */
class LazyChannel[A](override val api: GopherAPI) extends Input[A] with Output[A]
{

  def  cbread[B](f: ContRead[A,B] => Option[ContRead.In[A] => Future[Continuated[B]]], flwt: FlowTermination[B] ): Unit = 
    origin.cbread(f,flwt)

  def  cbwrite[B](f: ContWrite[A,B] => Option[(A,Future[Continuated[B]])], flwt: FlowTermination[B] ): Unit = 
    origin.cbwrite(f,flwt)

  lazy val origin = api.makeChannel[A]()

}

