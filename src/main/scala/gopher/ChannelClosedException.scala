package gopher

/**
 * throwed when channel is closed:
 *a) during attempt to write to closed channel.
 *b) during attempt to read from closed channel 'behind' last message.
 **/
class ChannelClosedException extends IllegalStateException("Channel is closed")
