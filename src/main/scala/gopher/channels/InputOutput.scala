package gopher.channels

import gopher.FlowTermination

import scala.concurrent.Future

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


}