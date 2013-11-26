package gopher.channels

import ch.qos.logback.classic.{Logger=>LogbackLogger}

trait ChannelBase[API <: ChannelsAPI[API]] {

   protected def executionContextProvider: ChannelsExecutionContextProvider
  
   protected def actorSystemProvider: ChannelsActorSystemProvider
   
   protected def loggerFactory: ChannelsLoggerFactory
   
   protected def logger: LogbackLogger
   
   protected def tag: String
   
   def api: ChannelsAPI[API]
   
   protected def makeTie(name: String) = api.makeTie(this.tag+"/"+name)(executionContextProvider, actorSystemProvider, loggerFactory)

   
}