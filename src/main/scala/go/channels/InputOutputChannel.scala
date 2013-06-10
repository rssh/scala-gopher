package go.channels

trait InputOutputChannel[A] extends InputChannel[A] with OutputChannel[A]
{

  
  trait InputOutputAsync extends InputAsync with OutputAsync
 
   
  override def async = new InputOutputAsync { }
  
  
  
}