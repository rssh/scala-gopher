package gopher.channels


import scala.concurrent._
import scala.concurrent.duration._
import gopher._
import scala.language.experimental.macros
import scala.language.postfixOps
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._

class ActorBackedChannel[A](override val api: GopherAPI) extends Channel[A](api)
{

  def  cbread[B](f: ContRead[A,B] => Option[ContRead.In[A] => Future[Continuated[B]]], flwt: FlowTermination[B] ): Unit = ???

  def  cbwrite[B](f: ContWrite[A,B] => Option[(A,Future[Continuated[B]])], flwt: FlowTermination[B] ): Unit = ???

  def isClosed: Boolean = ???

  def close(): Unit = ???

}

