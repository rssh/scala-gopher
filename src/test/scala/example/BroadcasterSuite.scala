package example.broadcast


import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import scala.async.Async._

import gopher._
import gopher.channels._

//import org.scalatest._




class CompileForever
{

  def aread[A]():Future[Option[A]] = ???

  def listen[A](out:Output[A]): Future[Unit] = 
  async {
       var finish = false;
       while(!finish) {
          val x = await(aread)
          // can't use foreach inside 'go' block.
          if (!x.isEmpty) {
             out.write(x.get)
          } else {
             finish = true
          }
       }
       ();
  }


}



