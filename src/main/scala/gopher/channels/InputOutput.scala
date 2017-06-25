package gopher.channels

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

import gopher.{ChannelClosedException, FlowTermination, GopherAPI}

import scala.concurrent.Future
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

        protected val internalFlowTermination = PromiseFlowTermination[Unit]()

        private val listeners = new ConcurrentHashMap[Long,ContRead.AuxE[C]]
        private val listenerIdsGen = new AtomicLong(0L)
        override val api: GopherAPI = thisInputOutput.api



        def internalRead(cr:ContRead[B,Unit]):Option[(ContRead.In[B]=>Future[Continuated[Unit]])] = {
          {
              implicit val ec = api.gopherExecutionContext
              Some {
                  case ContRead.Value(a) =>
                      other.cbwrite[Unit](cw => {
                          Some {
                              (a, Future successful cr)
                          }
                      }, internalFlowTermination)
                      Future successful Never // cr will be called after write.
                  case ContRead.Skip => Future successful cr
                  case ContRead.ChannelClosed =>
                      internalFlowTermination.doExit(())
                      Future successful Never
                  case ContRead.Failure(ex) => internalFlowTermination.throwIfNotCompleted(ex)
                      Future successful Never
              }
          }
        }

        thisInputOutput.cbread(internalRead,internalFlowTermination)

        internalFlowTermination.future.onComplete{ r =>
            listeners.forEach { (id, cr) =>
                val cre = cr.asInstanceOf[ContRead[A,cr.S]]
                cre.function(cre).foreach{ q =>
                    val n = r match {
                        case Failure(ex) => ContRead.Failure(ex)
                        case Success(_) => ContRead.ChannelClosed
                    }
                    api.continue(q(n),cre.flowTermination)
                }
            }
        }(api.gopherExecutionContext)

        override def cbread[D](f: (ContRead[C, D]) => Option[ContRead.In[C] => Future[Continuated[D]]], ft: FlowTermination[D]): Unit =
        {
            val id = listenerIdsGen.incrementAndGet()
            def wf(cr:ContRead[C,D]):Option[ContRead.In[C] => Future[Continuated[D]]]=
            {
              f(cr) map { q =>
                  listeners.remove(id)
                  q
              }
            }
            val cr = ContRead(f,this,ft)
            listeners.put(id,cr.asInstanceOf[ContRead.AuxE[C]])
            other.cbread(wf,ft)
        }

        override def cbwrite[B](f: (ContWrite[A, B]) => Option[(A, Future[Continuated[B]])], ft: FlowTermination[B]): Unit =
        {

            if (checkNotCompleted(ft)) thisInputOutput.cbwrite(f,ft)
        }

        private def checkNotCompleted[D](ft:FlowTermination[D]): Boolean =
        {
            if (internalFlowTermination.isCompleted) {
                implicit val ec = api.gopherExecutionContext
                internalFlowTermination.future.onComplete{
                    case Failure(ex) => ft.doThrow(ex)
                    case Success(_) => ft.doThrow(new ChannelClosedException())
                }
                false
            } else true
        }

    }

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
      new CompositionIOC[C](other)  {

      }

    override def |>[C](other:InputOutput[B,C]): CloseableInputOutput[A,C] = this.compose(other)


}