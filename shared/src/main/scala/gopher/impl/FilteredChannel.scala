package gopher.impl

import gopher._

class FilteredChannel[F[_],W,R](internal: Channel[F,W,R], p: R => Boolean) extends FilteredReadChannel[F,R](internal, p)
                                                                             with Channel[F,W,R]:

  override def addWriter(writer: Writer[W]): Unit =
    internal.addWriter(writer)

  override def close(): Unit =
    internal.close()

  override def isClosed: Boolean =
    internal.isClosed


class FilteredAsyncChannel[F[_],W,R](internal: Channel[F,W,R], p:  R => F[Boolean]) extends FilteredAsyncReadChannel[F,R](internal, p)
                                                                             with Channel[F,W,R]:

  override def addWriter(writer: Writer[W]): Unit =
    internal.addWriter(writer)

  override def close(): Unit =
    internal.close()

  override def isClosed: Boolean =
    internal.isClosed
    
  