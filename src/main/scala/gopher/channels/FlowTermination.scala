package gopher.channels


trait FlowTermination[A]
{

  def doThrow(e: Throwable): Unit

  def doExit(a:A): Unit

}
