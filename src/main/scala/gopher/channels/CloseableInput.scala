package gopher.channels

import gopher.GopherAPI

trait CloseableInput[A] extends Input[A] with DoneProvider[Unit] {

  closeableInputThis =>

  trait DoneSignalDelegate[T] extends CloseableInput[T] {
    val done = closeableInputThis.done
  }

  override def filter(p: A=>Boolean): CloseableInput[A] = new Filtered(p) with DoneSignalDelegate[A]

  override def map[B](g: A=>B): CloseableInput[B] = new Mapped(g) with DoneSignalDelegate[B]


  protected def applyDone[B](cr: ContRead[Unit,B]): Unit = {
    CloseableInput.applyDone(cr)(api)
  }


}

object CloseableInput
{

  def applyDone[B](cr: ContRead[Unit,B])(implicit api:GopherAPI): Unit = {
    cr.function(cr) foreach { g =>
      api.continue(g(ContRead.In.value(())),cr.flowTermination)
    }
  }


}