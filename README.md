  
 Implementation of go scopes and channels in scala.

 Scope

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
    
  Channels

    Channels is 
