package gopher.channels

import gopher.{ChannelClosedException, FlowTermination, GopherAPI}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait InputOutput[A,B] extends Input[B] with Output[A]
{

    thisInputOutput =>

    trait CbwriteDelegate extends Output[A]
    {
        override def cbwrite[B](f: (ContWrite[A, B]) => Option[(A, Future[Continuated[B]])], ft: FlowTermination[B]): Unit = thisInputOutput.cbwrite(f,ft)

    }

    class MappedIO[C](g: B=>C) extends Mapped[C](g)
                                  with CbwriteDelegate with InputOutput[A,C]

    override def map[C](g:B=>C): InputOutput[A,C] = new MappedIO(g)

    class FilteredIO(p: B=>Boolean) extends Filtered(p)
                                    with CbwriteDelegate with InputOutput[A,B]

    override def filter(p: B=>Boolean):InputOutput[A,B] = new FilteredIO(p)



    class CompositionIO[C](other:InputOutput[B,C]) extends InputOutput[A,C]
    {
        private val s = new ForeverSelectorBuilder(){
            override def api = thisInputOutput.api
        }

        s.readingWithFlowTerminationAsync(thisInputOutput,
            {(ec:ExecutionContext,ft:FlowTermination[Unit],a:B) =>
                implicit val iec = ec
                other.awrite(a) map (_ => ())
            }
        )

        protected val internalFuture = s.go


        // TODO: think, maybe we need intercept cbread here ?
        override def cbread[D](f: (ContRead[C, D]) => Option[(ContRead.In[C]) => Future[Continuated[D]]], ft: FlowTermination[D]): Unit =
        {
            if (checkNotCompleted(ft)) other.cbread(f,ft)
        }

        override def cbwrite[B](f: (ContWrite[A, B]) => Option[(A, Future[Continuated[B]])], ft: FlowTermination[B]): Unit =
        {
            if (checkNotCompleted(ft)) thisInputOutput.cbwrite(f,ft)
        }

        private def checkNotCompleted[D](ft:FlowTermination[D]): Boolean =
        {
            if (internalFuture.isCompleted) {
                implicit val ec = api.gopherExecutionContext
                internalFuture.onComplete{
                    case Failure(ex) => ft.doThrow(ex)
                    case Success(_) => ft.doThrow(new ChannelClosedException())
                }
                false
            } else true
        }

        override val api: GopherAPI = thisInputOutput.api
    }

    //TODO: add test suite.
    def compose[C](other:InputOutput[B,C]):InputOutput[A,C] =
      new CompositionIO[C](other)

    /**
      * Synonym for this.compose(other)
      */
    def |>[C](other:InputOutput[B,C]):InputOutput[A,C] = this.compose(other)

}


trait CloseableInputOutput[A,B] extends InputOutput[A,B] with CloseableInput[B]
{

    thisCloseableInputOutput =>

    class MappedIOC[C](g: B=>C) extends MappedIO[C](g) with DoneSignalDelegate[C] with CloseableInputOutput[A,C]

    override def map[C](g: B=>C): CloseableInputOutput[A,C] = new MappedIOC(g)

    class FilteredIOC(p: B=>Boolean) extends FilteredIO(p)
                                       with DoneSignalDelegate[B]
                                       with CloseableInputOutput[A,B]

    override def filter(p: B=>Boolean): CloseableInputOutput[A,B] = new FilteredIOC(p)

    class CompositionIOC[C](other: InputOutput[B,C]) extends CompositionIO(other)
                                                      with DoneSignalDelegate[C]
                                                      with CloseableInputOutput[A,C]

    override def compose[C](other: InputOutput[B,C]):CloseableInputOutput[A,C] =
      new CompositionIOC[C](other)

    override def |>[C](other:InputOutput[B,C]):CloseableInputOutput[A,C] = this.compose(other)


}