  
 Implementation of go scopes and channels in scala.
 =================================================

 Requirements:  scala 2.10.2 +
 ------------


 
 Scope
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



  Select loop
  ----------

