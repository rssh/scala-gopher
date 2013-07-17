  
# Implementation of go scopes and channels in scala.

## Requirements:  scala 2.10.2 +
   ------------


 
## Scope
   -----

 Library define 'goScope'  expression, which allows to use inside
 goScope go-like 'defer', 'panic' and 'recover' expression.
 Typical usage:

    import gopher._
    import java.io._

    object CopyFile {

       def main(args:Array[String]):Unit =
       {
        if (args.length != 3) {
          System.err.println("usage: copy in out");
        }
        copy(new File(args(1)), new File(args(2)))
       }

       def copy(inf: File, outf: File): Long =
        goScope {
         val in = new FileInputStream(inf)
         defer{ in.close() }
         val out = new FileOutputStream(outf);
         defer{ out.close() }
         out.getChannel() transferFrom(in.getChannel(), 0, Long.MaxValue)
       }
  
    }

  Here statements inside defer block are executed at the end of goScope block
  in reverse order.

  Take a look at introduction article about go-like control flow constructs:
                              http://blog.golang.org/defer-panic-and-recover

  Basically, goScope wrap it's argument into try/finalize block and rewrite
  *defer* calls to add defered code blocks to list of code blocks, which are
  executed in finalize.  *panic* calls are just throwing exception and 
  *recover* (which can be executed only inside *defer*) is returning a value
  of recover argument instead rethrowing exception:

    val s = goScope{ 
                defer{ recover("CCC") } 
                panic("panic message")
               "QQQ" 
            }

  will set *s* to "CCC".

  One question -- what to do if exception is occured inside one of defer blocks (?). By default we assume that statements inside *defer* have cleanup 
 semantics, so exception inside ones are suppressed. You can override this 
 semantics by calling special construction *throwSuppressed* inside first 
 *defer* block (which will evaluated last) i.e.

    goScope{
      defer{ throwSuppressed }
      ..........
    }

 will throw suppressed exceptions if one exists.

 Also we can get list of suppressed using suppressedExceptions construction:

    goScope{
      defer{ 
        if (suppressedExceptions.notEmpty) {
          System.err.println("suppresses exceptions:")
          for(e <- suppressedExceptions) e.printStackTrace
        }
      }
      ..........
    }

    
  Go statement
  ------------

  Go statement starts the execution of expression in independent thread.
  We map one to Future call which use current implicit execution statement, 
  so next 'go-like' code

    go {
      expr:A
    }
  
  have type *Future[A]* and mapped into plain scala as combination of *Future*
  and *goScope* . 

  Channels
  --------

    Channels is a way for organizing go-like message passing. Basically you
can look on it as on classic blocked queue. Different 'goroutines', executed
in different flows can exchange messages via channels.


     val channel = makeChannel[Int];

     go {
       for(i <- 1 to 10) channel <~ i
     }

     go {
       val i1 = channel? 
       val i2 = channel?
     }

  
  *channel <~ i* - send i to channel (it is possible to use '!' as synonym, to
  provide interface, simular to actors), *i = channel ?* - blocked read 
  of channell. Note, that unlike akka, default read and write operations are
  blocked.  Unlike go, we also provide 'immediate' and 'timeouted' versions
  of read/write operations.

  Select loop
  ----------

  May-be one of most unusual language constructions in go is 
  'select statement' which work in somewhat simular to unix 'select' syscall:
  from set of blocking operations select one which is ready for input/output
  and run it.
 
  Gopher provides simular functionality with 'select loops':

     import gopher._


     for( s <- select ) 
      s match {
        case `channelA` ~> (i:Int) => ..do-something-with-i
        case `channelB' ~> (ch: Char) => .. do-something-with-b
      }
   
  Here we read in loop from channelA or channelB. 

  Body of select loop must consists only from one *match* statement where 
  patterns in *case* clauses must have form *channel ~> (v:Type)*  
  (for reading from channel) or *channel <~ v* (for writing).
 
  Yet one example: 

     val channel = makeChannel[Int](100)
     
     
     go {
       for( i <- 1 to 1000) 
         channel <~ i 
     }
     
     var sum = 0;
     val consumer = go {
       for(s <- select) {
          s match {
             case `channel` ~> (i:Int) =>
                     sum = sum + i
                     if (i==1000)  s.shutdown()
          }
       }
       sum
     }
  
     Await.ready(consumer, 5.second)

   Note the use of *s.shutdown* method for ending select loop. 


  Interaction with Actors
  -----------------------

   We can bind channel output to actor (i.e. all, what we will write to channel
  will be readed to actor) with call 

    bindChannelRead[A](read: InputChannel[A], actor: ActorRef)

   and bind channel to actorsystem, by creating actor which will push all input
   into channel:

    bindChannelWrite[A: ClassTag](write: channels.OutputChannel[A], 
                                  name: String)
                                       (implicit as: ActorSystem): ActorRef 




