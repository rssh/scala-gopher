package gopher.channels


trait ChannelBase[API <: ChannelsAPI[API]] {

   protected def executionContextProvider: ChannelsExecutionContextProvider
  
   protected def actorSystemProvider: ChannelsActorSystemProvider
   
   def api: ChannelsAPI[API]
   
   protected def makeTie = api.makeTie(executionContextProvider, actorSystemProvider)

   
}