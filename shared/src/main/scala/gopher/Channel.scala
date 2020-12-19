package gopher

import cps._
import java.io.Closeable

trait Channel[F[_],W,R] extends WriteChannel[F,W] with ReadChannel[F,R] with Closeable:

  override protected def gopherApi: Gopher[F]

end Channel

