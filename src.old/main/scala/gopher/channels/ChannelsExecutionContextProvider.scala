package gopher.channels

import scala.concurrent._
import java.util.concurrent._

trait ChannelsExecutionContextProvider {

  def executionContext: ExecutionContext
  
}

object DefaultChannelsExecutionContextProvider extends ChannelsExecutionContextProvider
{
  
  override lazy val executionContext = 
  {
    val es = new ThreadPoolExecutor(2,20,10,TimeUnit.SECONDS, new LinkedBlockingDeque[Runnable]())
    ExecutionContext.fromExecutorService(es)
  }

}
