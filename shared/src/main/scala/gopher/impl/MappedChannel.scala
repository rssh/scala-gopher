package gopher.impl

import gopher._

class MappedChannel[F[_],W,RA,RB](internal: Channel[F,W,RA], f: RA=>RB) extends MappedReadChannel[F,RA,RB](internal, f)
                                                                             with Channel[F,W,RB]:

  override def addWriter(writer: Writer[W]): Unit =
    internal.addWriter(writer)

  override def close(): Unit =
    internal.close()


class MappedAsyncChannel[F[_],W,RA,RB](internal: Channel[F,W,RA], f: RA=>F[RB]) extends MappedAsyncReadChannel[F,RA,RB](internal, f)
                                                                             with Channel[F,W,RB]:

  override def addWriter(writer: Writer[W]): Unit =
    internal.addWriter(writer)

  override def close(): Unit =
    internal.close()
