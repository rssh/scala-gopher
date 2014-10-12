  
## Gopher: asynchronous implementation of go-like channels/selectors in scala
=======

### Dependences:    
 
 * scala 2.11.2 +
 * akka 2.3.6 +
 * scala-async 0.9.2

#### Download: 

    libraryDependencies += "com.github.rssh" %% "scala-gopher" % "0.99.0"


## Overview

   Scala-gopher is a scala library, build on top of akka and SIP-22 async, which provide implementation of 
 CSP [Communicate Sequential Processes] primitives, known as 'Go-like channels'. Also analogs of go/defer/recover control flow constructions are provided. 

Note, that this is not an emulation of go language constructions in scala, but rather reimplementation of key ideas in 'scala-like' maner.
  


### Initialization
 
 You need instance of gopherApi for creating channels and selectors.  The most easy way is to use one as Akka extension:
 
      import akka.actors._
      import gopher._
  
      ......
     
      val actorSystem = ActorSystem.create("system")
      val gopherApi = Gopher(actorSystem)    
 
 In akka.conf we can place config values in 'gopher' entry. 
 
## Control flow constructions:

### goScope

 `goScope[T](body: =>T)` is expression, which allows to use inside `body` go-like 'defer' and 'recover' expression.
 
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

  Here statements inside defer block are executed at the end of goScope block in reverse order.

  Basically, inside goScope we can use two pseudofunctions:

* `defer(body: =>Unit):Unit` - defer execution of `body` until the end of `go` or `goScope` block and previous defered blocks.
* `recover[T](f:PartialFunction[Throwable,T]):Boolean` -- can be used only within `defer` block with next semantics:
* * if exeception was raised inside `go` or `goScope` than `recover` try to apply  `f` to this exception and
* * * if   `f` is applicable - set `f(e)` as return value of the block and return true
* * * otherwise - do nothing and return false 
* * during normal exit - return fase.

You can look on `defer` as on stackable finally clauses, and on `defer` with `recover` inside as on `catch` clause. Small example:

      val s = goScope{ 
                defer{ recover{
                         case ex: Throwable => "CCC"
                      } } 
                throw new Exception("")
               "QQQ" 
            }


  will set `s` to "CCC".

    

### go 

  `go[T](body: =>T)(implicit ex:ExecutionContext):Future[T]` starts asyncronics execution of `body` in provided execution context. Inside go we can use `defer`/`recover` clauses and blocked read/write channel operations.  
  
  Basucally, go implemented on top of [SIP-22](http://docs.scala-lang.org/sips/pending/async.html) async and share the same limitations.   

## Channels

You can look on channel as on classic blocked queue with fixed size. Different execution flows can exchange messages via channels.


     val channel = gopherApi.makeChannel[Int];

     go {
       channel.write(a)
     }
     ......
     go {
       val i = channel.read 
     }

  
  
* `channel.write(x)` - send x to channel and wait until one will be send (it is possible us as synonims `channel<~x` and `channel!x` if you prefere short syntax)
* `channel.read` or `(channel ?)` - blocking read

Blocking operations can be used only inside `go` or `Async.await` blocks. 

Outside we can use asynchronics version:

* `channel.awrite(x)` will write `x` and return to us `Future[Unit]` which will be executed after x will send
* `channel.aread` will reaturn feature to value, which will be readed.

Also channels can be closed, after this attempt to write will cause throwing of 'ClosedChannelException', reading is possible up to 'last written value', after this attempt to read will cause same exception.  

Note, that closing channels is not mandatory, unreachable channels are garbage-collected regardless of they are closed or not. 

Also you can use only 'Input' or 'Output' interfaces, where appropriative read/write operations is defined. 
For input we have defined usual collection functions, like `map`, `zip`, `takeN` . Scala Iterable can be represented as `channels.Input` via method `gopherApi.iterableInput`. Also we can use scala futures as channels, which produce one value and then closes. For obtaining such input use  `gopherApi.futureInput`.

`|` (i.e. or) operator used for merged inputs, i.e. `(x|y).read` will read vaue from channel x or y when one will be available.


Also note, that you can provide own Input and Output implementations by implementing callback `cbread` and `cbwrite` methods.


## Select loops

  'select statement' is somewhat simular to unix 'select' syscall:
  from set of blocking operations select one which is ready for input/output and run it.

  The common pattern of channel processing in go language is wrap select operation into endless loop.
 
  Gopher provides simular functionality with 'select loops':

    go{
     for( s <- gopherApi.select.forever) 
      s match {
        case i:channelA.read => ..do-something-with-i
        case ch:channelB.read .. do-something-with-b
      }
    }
   
  Here we read in loop from channelA or channelB. 

  Body of select loop must consists only from one `match` statement where 
  left parts in `case` clauses must have form
   
  * `v:channel.read` (for reading from channel) 
  * `v:Type if (v==read(ch))` (for reading from channel or future) 
  * `v:channel.write if (v==expr)` (for writing `expr` into channel).
  * `v:Type if (v==write(ch,expr))` (for writing `expr` into channel).
  * `_` - for 'idle' action.


  For endless loop inside go we can use shortcut with syntax of partial function:
    
```
     gopherApi.select.forever{ 
         case i:channelA.read => ..do-something-with-i
         case ch:channelB.read .. do-something-with-b
     }
```
    
 
  Inside case actions we can use blocking read/writes and await operations.  Call of doExit in implicit instance of `FlowTermination[T]`  (for forever loop this is `FlowTermination[Unit]`) can be used for exiting from loop.
  
  Example: 

      val channel = gopherApi.makeChannel[Int](100)
     
      val producer = channel.awrite(1 to 1000)
     
      @volatile var sum = 0;
      val consumer = gopherApi.select.forever{
          case i: channerl.read  =>
                  sum = sum + i
                  if (i==1000)  {
                    implictily[FlowTermination[Unit]].doExit(())
                  }
      }
     
      Await.ready(consumer, 5.second)


   For using select operation not enclosed in loop, scala-gopher provide
   *select.once* syntax:
   
```
      gopherApi.select.once{
         case i: channelA.read => s"Readed(${i})"
         case x:channelB.write if (x==1) => s"Written(${x})" 
      }
```


   Such form can be called from any environment and will return `Future[String]`.  Inside `go` you can wrap this in await of use 'for' syntax as with `forever`
    

```
       go {
         .....
         val s = for(s <-gopherApi.select.once) 
             s match {
               case i: channelA.read => s"Readed(${i})"
               case x: channelB.write if (x==1) => s"Written(${x})" 
             }
      
        }  
```


## Unsugared interfaces
   
   It's not worse to know that exists gopher API without macro-based syntax sugar.  
   
      (
       new ForeverSelectorBuilder(gopherApi)
            .reading(ch1){ x => something-x }
            .writing(ch2,y){ y => something-y }
            .idle(something idle).go
      )
    
   can be used instead appropriative macro-based call.  
   
   And for really tricky things exists even low-level interface, which can combine computations by adding to functional interfaces, simular to continuations:
   
     {
      val selector = new Selector[Unit](gopherApi)
      selector.addReader(ch1, cont=>Some{gen=> something-x
                                         Future successful cont
                                     }
                        )
      selector.addWriter(ch2, cont=>Some{(y,{something y;
                                             Future successful cont
                                           })})                  
      selector.addIdle(cont => {..do-something-when-idle; Future successful cont})
     } 
   
   Please, consult with source code for details.


## Additional Informatiom
   ----------------------

* API reference: http://rssh.github.io/scala-gopher/api/index.html#package
* source code: https://github.com/rssh/scala-gopher

  Some related links:

* [Communicating Sequential Processes book by Tony Hoare](http://www.usingcsp.com)
* [brief history of CSP in Bell-labs](http://swtch.com/~rsc/thread/)
* [introduction article about go defer/recover](http://blog.golang.org/defer-panic-and-recover)   
    
