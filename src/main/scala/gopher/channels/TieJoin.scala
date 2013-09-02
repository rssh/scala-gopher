package gopher.channels

trait TieJoin {
  
  def processExclusive[A](f: =>A, whenLocked: =>A): A
  
  def shutdown(): Unit
  
}

trait TieReadJoin[+A] extends TieJoin {

  def putNext(action: ReadAction[A]): Unit
  
  
}

trait TieWriteJoin[A] extends TieJoin {
  
  def putNext(action: WriteAction[A]): Unit
 
  
}