package gopher.impl

import gopher._

class ChFlatMappedChannel[F[_],W,RA,RB](internal: Channel[F,W,RA], f: RA=>ReadChannel[F,RB]) extends ChFlatMappedReadChannel[F,RA,RB](internal, f)
                                                                             with Channel[F,W,RB]:

  override def addWriter(writer: Writer[W]): Unit =
    internal.addWriter(writer)

  override def close(): Unit =
    internal.close()

