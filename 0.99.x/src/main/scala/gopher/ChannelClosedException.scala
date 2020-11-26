package gopher

/**
 * throwed when channel is closed:
 *<ul>
 *<li> during attempt to write to closed channel. </li>
 *<li> during attempt to read from closed channel 'behind' last message. </li>
 *</ul>
 **/
class ChannelClosedException extends IllegalStateException("Channel is closed")
