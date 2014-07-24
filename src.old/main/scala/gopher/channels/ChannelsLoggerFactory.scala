package gopher.channels

import ch.qos.logback.classic.{Logger=>LogbackLogger}

trait ChannelsLoggerFactory {

  /**
   * create logger for class oe ownerClass with name name.
   */
  def  logger[T](ownerClass: Class[T], ownerName: String): LogbackLogger
  
}


/**
 * Default facotry, which use standard StaticLoggerBinder
 */
object DefaultChannelsLoggerFactory extends ChannelsLoggerFactory
{

  import org.slf4j._
  import org.slf4j.impl._
  
  def  logger[T](ownerClass: Class[T], ownerName: String): LogbackLogger =
    loggerFactory.getLogger(ownerClass.getName()+"/"+ownerName).asInstanceOf[LogbackLogger]
  
  
  private[this] lazy val loggerFactory = StaticLoggerBinder.getSingleton().getLoggerFactory();

}



