package gopher

import scala.reflect._
import scala.concurrent._
import akka.actor._
import gopher.channels.naive.GBlockedQueue

package object channels {

  object Naive
  {
    implicit val api = _root_.gopher.channels.naive.NaiveChannelsAPI.instance
    
    type IChannel[A] = api.IChannel[A]
    type OChannel[A] = api.OChannel[A]
    type IOChannel[A] = api.IChannel[A]
    
  }
  
  
  
  def make[A: ClassTag](capacity:  Int = 1000)
       (implicit api: ChannelsAPI[_], 
        ecp: ChannelsExecutionContextProvider = DefaultChannelsExecutionContextProvider,
        asp: ChannelsActorSystemProvider = DefaultChannelsActorSystemProvider ): api.IOChannel[A] = 
    {
      val retval = api.makeChannel[A](capacity)
      retval;
    }
  

  
}