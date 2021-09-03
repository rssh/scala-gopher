package gopher

class ChannelClosedException(
  debugInfo: String = ""
) extends RuntimeException(s"channel is closed. ${debugInfo}")
