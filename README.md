  
## Gopher: asynchronous implementation of go-like channels/selectors in scala
=======

### Dependences:    
 
 * scala 2.11.7 +
 * akka 2.4.2 +
 * scala-async 0.9.5

#### Download: 

    libraryDependencies += "com.github.rssh" %% "scala-gopher" % "0.99.6"

(or `0.99.7-SNAPSHOT` for development version).

Scala-gopher is open source (license is Apache2); binaries are available from the maven-central repository.

## Overview

   Scala-gopher is a scala library, build on top of Akka and SIP-22 async, which provide an implementation of 
 CSP [Communicate Sequential Processes] primitives, known as 'Go-like channels.' Also, analogs of go/defer/recover control-flow constructions are provided. 

Note, which this is not an emulation of go language structures in Scala, but rather a reimplementation of the main ideas in 'scala-like' manner.
  


### Initialization
 
 You need an instance of gopherApi for creating channels and selectors.  The easiest way is to use one as Akka extension:
 
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
~~~ scala
import gopher._
import java.io._

object CopyFile {

  def main(args: Array[String]): Unit = {
    if (args.length != 3) {
      System.err.println("usage: copy in out");
    }
    copy(new File(args(1)), new File(args(2)))
  }

  def copy(inf: File, outf: File): Long =
    goScope {
      val in = new FileInputStream(inf)
      defer {
        in.close()
      }
      val out = new FileOutputStream(outf);
      defer {
        out.close()
      }
      out.getChannel() transferFrom(in.getChannel(), 0, Long.MaxValue)
    }

}
~~~
  Here statements inside defer block executed at the end of goScope block in reverse order.

  Inside goScope we can use two pseudo functions:

* `defer(body: =>Unit):Unit` - defer execution of `body` until the end of `go` or `goScope` block and previous defered blocks.
* `recover[T](f:PartialFunction[Throwable,T]):Boolean` -- can be used only within `defer` block with next semantics:
* * if exception was raised inside `go` or `goScope` than `recover` try to apply  `f` to this exception and
* * * if   `f` is applicable - set `f(e)` as return value of the block and return true
* * * otherwise - do nothing and return false 
* * during normal exit - return false.

You can look on `defer` as on stackable finally clauses, and on `defer` with `recover` inside as on `catch` clause. Small example:

~~~ scala
val s = goScope{ 
           defer{ recover{
                     case ex: Throwable => "CCC"
           }    } 
           throw new Exception("")
           "QQQ" 
        }
~~~

  will set `s` to "CCC".

    

### go 

  `go[T](body: =>T)(implicit ex:ExecutionContext):Future[T]` starts asynchronous execution of `body` in provided execution context. Inside go we can use `defer`/`recover` clauses and blocked read/write channel operations.  
  
  Go implemented on top of [SIP-22](http://docs.scala-lang.org/sips/pending/async.html) async and share the same limitations.   

## Channels

Channels are used for asynchronous communication between execution flows.

When using channel inside *go* block, you can look at one as on classic blocked queue with fixed size with methods read and write:


     val channel = gopherApi.makeChannel[Int];

     go {
       channel.write(a)
     }
     ......
     go {
       val i = channel.read 
     }

  
  
* `channel.write(x)` - send x to channel and wait until one will be sent (it is possible us as synonyms `channel<~x` and `channel!x` if you prefer short syntax)
* `channel.read` or `(channel ?)` - blocking read

Blocking operations can be used only inside `go` or `Async.await` blocks. 

Outside we can use asynchronous version:

* `channel.awrite(x)` will write `x` and return to us `Future[Unit]` which will be executed after x will send
* `channel.aread` will return future to the value, which will be read.

Also, channels can be closed. After this attempt to write will cause throwing 'ClosedChannelException'. Reading will be still possible up to 'last written value', after this attempt to read will cause the same exception.  

Note, closing channels is not mandatory; unreachable channels are garbage-collected regardless of they are closed or not. 


Channels can be buffered and unbuffered. In a unbuffered channel, write return control to the caller after another side actually will start processing; buffered channel force provider to wait only if internal channel buffer is full.

Also, you can use only `Input` or `Output` interfaces, where appropriative read/write operations are defined. 
For `Input`, exists usual collection functions, like `map`, `zip`, `takeN`, `fold` ... etc. Scala Iterable can be represented as `channels.Input` via method `gopherApi.iterableInput`. Also, we can use Scala futures as channels, which produce one value and then closes. For obtaining such input use  `gopherApi.futureInput`.

`|` (i.e. or) operator used for merged inputs, i.e. `(x|y).read` will read a value from channel x or y when one will be available.

For each input and output you can create a facility with tracked timeout, i.e. if `in` is input, then
~~~ scala
 val (inReady, inTimeouts) = in.withInputTimeouts(10 seconds)
~~~
will return two inputs, where reading from `inReady` will return the same as reading from `in`. And if waiting for reading takes longer than 10 seconds then the value of timeout will be available in `inTimeouts`. Analogically we can create output with timeouts:
~~~ scala
 val (outReady, outTimeouts) = out.withOutputTimeouts(10 seconds)
~~~


Also, note that you can provide own Input and Output implementations by implementing callback `cbread` and `cbwrite` methods.


## Select loops and folds

  'select statement' is somewhat similar to Unix 'select' syscall:
  from a set of blocking operations select one who is ready to input/output and run it.

  The typical pattern of channel processing in go language is to wrap select operation into an endless loop.
 
  Gopher provides similar functionality:

~~~ scala
go{
  for( s <- gopherApi.select.forever) 
    s match {
      case i:channelA.read => ..do-something-with-i
      case ch:channelB.read .. do-something-with-b
  }
}
~~~
   
  Here we read in the loop from channelA or channelB. 

  Body of select loop must consist only of one `match` statement where 
  left parts in `case` clauses must have the following form
   
  * `v:channel.read` (for reading from channel) 
  * `v:Tye if (v==read(ch))` (for reading from channel or future) 
  * `v:channel.write if (v==expr)` (for writing `expr` into channel).
  * `v:Type if (v==write(ch,expr))` (for writing `expr` into channel).
  * `_` - for 'idle' action.


  For endless loop inside `go` we can use the shortcut with syntax of partial function:
    
~~~ scala
     gopherApi.select.forever{ 
         case i:channelA.read => ... do-something-with-i
         case ch:channelB.read ... do-something-with-b
     }
~~~
    
 
  Inside case actions, we can use blocking read/writes and await operations.  Call of doExit in the implicit instance of `FlowTermination[T]` (for a forever loop this is `FlowTermination[Unit]`) can be used for exiting from the loop.
  
  Example: 

~~~ scala
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
~~~

   Combination from variable and select loop better modeled with help 'fold over select':

~~~ scala
val consumer = gopherApi.select.afold(0) { (state, selector) =>
   selector match {
        case i: channel.read  =>
                          val nstate = state + i
                          if (i==1000) {
                            implictily[FlowTermination[Int]].doExit(nstate)
                          }
                          nstate
   }
}
~~~


   More than one variables in state can be modelled with partial function case syntax:

~~~ scala
val fib = select.afold((0,1)) { case ((x,y), s) =>
    s match {
      case x:channel.write => (y,y+x)
      case q:quit.read => CurrentFlowTermination.exit((x,y))
    }
}
~~~


   For using select operation not enclosed in a loop, scala-gopher provide
   *select.once* syntax:
   
~~~ scala
gopherApi.select.once{
  case i: channelA.read => s"Readed(${i})"
  case x:channelB.write if (x==1) => s"Written(${x})" 
}
~~~


   Such form can be called from any environment and will return `Future[String]`.  Inside `go` you can wrap this in await of use 'for' syntax as with `forever`.
    

~~~ scala
go {
  .....
  val s = for(s <-gopherApi.select.once) 
             s match {
               case i: channelA.read => s"Readed(${i})"
               case x: channelB.write if (x==1) => s"Written(${x})" 
             }
      
}  
~~~


 and afold become fold:

~~~ scala
go {
  ...
  val sum = select.fold(0) { (n,s) =>
             s match {
               case x: channelA.read => n+x
               case q: quit.read => CurrentFlowTermination.exit(n)
             }
            }
}
~~~

## Effected{Input,Output,Channel}

  One useful programming pattern, often used in CSP-style programming: have a channel from wich we read (or to where we write) as a part of a state.  In Go language, this is usually modelled as a mutable variable, changed inside the same select statement, where one is read/written.

  In scala-gopher, we have the ability to use a technique of 'EffectedChannel', which can be seen as an entity, which holds channel, can be used in read/write and can be changed only via effect (operation, which accepts previous state and return next).

Let's look on example:

~~~ scala
 def generate(n:Int, quit:Promise[Boolean]):Channel[Int] =
  {
    val channel = makeChannel[Int]()
    channel.awriteAll(2 to n) andThen (_ => quit success true)
    channel
  }

 def filter(in:Channel[Int]):Input[Int] =
  {
     val filtered = makeChannel[Int]()
     val sieve = makeEffectedInput(in)
     sieve.aforeach { prime =>
            sieve <<= (_.filter(_ % prime != 0))
            filtered <~ prime
      }
    filtered
  }
~~~

Here in 'filter', we generate a set of prime numbers, and make a sieve of Eratosthenes by sequentially applying 'filter' effect to state of sieve EffectedInput.


## Transputers

  The logic of data transformation between channels can be encapsulated in special `Transputer` concept. (Word 'transputer' was chosen
 as a reminder about INMOS processor, for which one of the first CSP languages, Occam, was developed).  You can view on transputer as 
 representation of restartable process that consists from:
 
 * Set of named input and output ports.
 * Logic for propagating information from the input to the output ports.
 * Possible state
 * Logic of error recovering.

I.e. we saw that Transputer is similar to Actor with the following difference: 
 When Actor provides reaction to incoming messages from the mailbox and sending signals to other actors, Transputers provide processing of incoming messages from input ports and sending outcoming messages to output ports. When operations inside Actor must not be blocked, operations inside Transputer can wait.

Transformers are build hierarchically with help of 3 operations:

 * select  (logic is execution of a select statement )
 * parallel combination  (logic is parallel execution of parts)
 * replication           (logic is parallel execution of a set of identical transformers.)

### Select transputer

 Let's look at a simple example: transputer with two input ports and one output. When same number has come from `inA` and `inB`, then
transputer prints `Bingo` on console and output this number to `out`:

~~~ scala
 trait BingoTransputer extends SelectTransputer
 {
    val inA = InPort[Int]
    val inB = InPort[Int]
    val out = OutPort[Boolean]

    loop {
      case x:inA.read =>
             y = inB.read
             out.write(x==y)
             if (x==y) {
               Console.println(s"Bingo: ${x}")
             }
    }

 }
~~~

  A select loop is described in `loop` statement.
  
  To create transputer we can use `gopherApi.makeTransputer` call:
~~~ scala
val bing = gopherApi.makeTransputer[BingoTransputer]
~~~
  after the creation of transputer, we can create channels, connect one to ports and start transformer. 
  
~~~ scala
val inA = makeChannel[Int]()
bingo.inA.connect(inA)
val inB = makeChannel[Int]()
bingo.inB.connect(inB)
val out = makeChannel[Int]()
bingo.out.connect(out)

val shutdownFuture = bingo.start()
~~~


  Then after we will write to `inA` and `inB` values `(1,1)` then `true` will become available for reading from `out`.

#### Error recovery 
  
  On an exception from a loop statement, transputer will be restarted with ports, connected to the same channels. Such behaviour is default; we can configure one by setting recovery policy:
  
~~~ scala
val t = makeTransputer[MyType].recover {
           case ex: MyException => SupervisorStrategy.Escalate
        }
~~~  
 
 Recovery policy is a partial function from throwable to akka `SupervisorStrategy.Direction`. Escalated exceptions are passed to parent transputers or to TransputerSupervisor actor, which handle failures according to akka default supervisor strategy.
 
 How many times transputer can be restarted within given period can be configured via failureLimit call:
 
~~~ scala
 t.failureLimit(maxFailures = 20, windowDuration = 10 seconds)
~~~
 
 This setting means that if 20 failures will occur during 10 seconds, then exception Transputer.TooManyFailures will be escalated to parent.
 
### Par transputers.
 
 'Par' is a group of transputers running in parallel. Par transputer can be created with the help of plus operator:
 
~~~ scala 
val par = (t1 + t1 + t3)
par.start()
~~~
 
 When one from `t1`, `t2`, ...  is stopped or failed, then all other members of `par` are stopped. After this `par` can be restarted according to current recovery policy.


### Replication 
 
 Replicated transputer is a set of identical transputers t_{i}, running in parallel.  It cam be created with `gopherApi.replicate` call. Next code fragment:
 
~~~ scala 
val r = gopherApi.replicate[MyTransputer](10)
~~~
 
 will produce ten copies of MyTransputer (`r` will be a container transputer for them). Ports of all replicated internal transputers will be shared with ports of the container. (I.e. if we will write something to input port then it will be read by one of the replicas; if one of the replicas will write something to out port, this will be visible in out port of container.)
 
 Mapping from a container to replica port can be changed from sharing to other approaches, like duplicating or distributing, via applying port transformations.
 
 For example, next code fragment:

~~~ scala
r.inA.duplicate()
 .inB.distribute( _.hashCode )
~~~
 
  will set port `inA` be duplicated in replicas (i.e. message, send to container port `inA` will be received by each instance) and messages from `inB` will be distributed by hashcode: i.e. messages with the same hashcode will be directed to the same replica. Such behaviour is useful when we keep in replicated transputer some state information about messages.
  
  Stopping and recovering of replicated transformer is the same as in `par` (i.e. stopping/failing of one instance will cause stopping/failing of container)
  
  Also note, that we can receive a sequence of replicated instances with the help of `ReplicateTransformer.replicated` method.

## Unsugared interfaces
   
   It is worth to know that exists gopher API without macro-based syntax sugar.  
   
~~~ scala
(
   new ForeverSelectorBuilder(gopherApi)
          .reading(ch1){ x => something-x }
          .writing(ch2,y){ y => something-y }
          .idle(something idle).go
)
~~~
    
   can be used instead of appropriative macro-based call.  
   
   Moreover, for tricky things exists even low-level interface, which can combine computations by adding to functional interfaces, similar to continuations:
   
~~~ scala
{
  val selector = new Selector[Unit](gopherApi)
  selector.addReader(ch1, cont=>Some{ in => something-x
                                        Future successful cont
                                    }
                    )
 selector.addWriter(ch2, cont=>Some{(y,{something y;
                                           Future successful cont
                    })})                  
 selector.addIdle(cont => {..do-something-when-idle; Future successful cont})
} 
~~~
   
   Please, consult with source code for details.


## Additional Informatiom
   ----------------------

* API reference: http://rssh.github.io/scala-gopher/api/index.html#package
* source code: https://github.com/rssh/scala-gopher
* presentations: 
     * Odessa Java/Scala Labs; Kiev Scala Meetup: Oct. 2014: http://www.slideshare.net/rssh1/scala-gopher2014  
     * Wix R&D meetup. Mart 2016: http://www.slideshare.net/rssh1/csp-scala-wixmeetup2016


  Some related links:

* [Communicating Sequential Processes book by Tony Hoare](http://www.usingcsp.com)
* [brief history of CSP in Bell-labs](http://swtch.com/~rsc/thread/)
* [introduction article about go defer/recover](http://blog.golang.org/defer-panic-and-recover)   
    
