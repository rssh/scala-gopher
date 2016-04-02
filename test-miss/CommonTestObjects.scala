package gopher.channels

import org.scalatest._
import gopher._
//import gopher.tags._
import akka.actor._
import scala.concurrent._
import scala.concurrent.duration._

object CommonTestObjects {

    lazy val actorSystem = ActorSystem.create("system")
    lazy val gopherApi = Gopher(actorSystem)
  
}
