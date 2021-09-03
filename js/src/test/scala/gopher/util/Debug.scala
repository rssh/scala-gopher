package gopher.util

import java.util.logging.{Level => LogLevel}

object Debug {

  type InMemoryLog = java.util.concurrent.ConcurrentLinkedQueue[(Long, String, Throwable)]

  def inMemoryLogFun(inMemoryLog: InMemoryLog): (LogLevel, String, Throwable|Null) => Unit =
    (level,msg, ex) => inMemoryLog.add((Thread.currentThread().getId(), msg,ex)) 

  def showInMemoryLog(inMemoryLog: InMemoryLog): Unit = {
    while(!inMemoryLog.isEmpty) {
      val r = inMemoryLog.poll()
      if (r != null) {
            println(r)
      }
    }
  }
  

  def showTraces(maxTracesToShow: Int): Unit = {
    val traces = Thread.getAllStackTraces();
    val it = traces.entrySet().iterator()
    while(it.hasNext()) {
      val e = it.next();
      println(e.getKey());
      val elements = e.getValue()
      var sti = 0
      var wasPark = false
      while(sti < elements.length && sti < maxTracesToShow && !wasPark) {
          val st = elements(sti)
          println(" "*10 + st)
          sti = sti + 1;
          wasPark = (st.getMethodName == "park")
      }
    }
  } 
  
 
}