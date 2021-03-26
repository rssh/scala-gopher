  
## Gopher: asynchronous implementation of go-like channels/selectors in scala
=======

### Dependences:    
 
For scala 3.0.0-RC1:

    libraryDependencies += "com.github.rssh" %% "scala-gopher" % "2.0.0-RC1"

For scala2: 

    libraryDependencies += "com.github.rssh" %% "scala-gopher" % "0.99.15"


Scala-gopher is open source (license is Apache2); binaries are available from the maven-central repository.

## Overview

   Scala-gopher is a scala library, build on top of dotty-cps-async, which provide an implementation of 
 CSP [Communicate Sequential Processes] primitives, known as 'Go-like channels.'  

Note, which this is not an emulation of go language structures in Scala, but rather a reimplementation of the main ideas in 'scala-like' manner.
  

### Initialization
 
 You need a given of gopherApi for creating channels and selectors.  
 
      import gopher._
  
      ......
     
      given Gopher[Future]()    
 
 type parameter is a monad, which should implement CpsSchedulingMonad typeclass.
 

## Channels

Channels are used for asynchronous communication between execution flows.
When using channel inside async block, you can look at one as on classic blocked queue with fixed size with methods read and write:


     val channel = makeChannel[Int];

     async {
       channel.write(a)
     }
     ......
     async {
       val i = channel.read()
     }

  
* `channel.write(x)` - send x to channel and wait until one will be sent (it is possible us as synonyms `channel<~x` and `channel!x` if you prefer short syntax)
* `channel.read` or `(channel ?)` - blocking read

Blocking operations can be used only inside `await` blocks. 

Outside we can use asynchronous version:

* `channel.awrite(x)` will write `x` and return to us `Future[Unit]` which will be executed after x will send
* `channel.aread()` will return future to the value, which will be read.


Channels can be closed. After this attempt to write will cause throwing 'ClosedChannelException.' Reading will be still possible up to 'last written value', after this attempt to read will cause the same exception. Also, each channel provides `done` input for firing close events.

Closing channels are not mandatory; unreachable channels are garbage-collected regardless of they are closed or not. 

Channels can be buffered and unbuffered. In a unbuffered channel, write return control to the caller after another side actually will start processing; buffered channel force provider to wait only if internal channel buffer is full.

Also, you can use only `Input` or `Output` interfaces, where an appropriative read/write operations are defined. 
For `Input`, exists usual collection functions, like `map`, `zip`, `takeN`, `fold` ... etc. 

For example, let we have the direct port of golang code:
~~~ scala

val channel = gopher.makeChannel[Int](100)
     
val producer = channel.awrite(1 to 1000)
     
@volatile var sum = 0;
val consumer = async {
    var done = false
    while(!done)
      val i = channel.read()
      sum = sum + i
      if i==1000 then
           done = true
}
     
~~~

last loop can be repharased in more scala wat as:

~~~ scala
val sum = (channel.take(1000)).fold(0)((s,i) => s+i)
~~~

Scala Iterable can be represented as `ReadChannel` via extension method `asReadChannel`. 
 
Also, we can use Scala futures as channels, which produce one value and then closes. For obtaining such input use  `gopherApi.futureInput`.

`|` (i.e. or) operator used for merged inputs, i.e. `(x|y).read` will read a value from channel x or y when one will be available.

Also, note that you can provide own Input and Output implementations by implementing callback `cbread` and `cbwrite` methods.


## Select loops and folds

  'select statement' is somewhat similar to Unix 'select' syscall:
  from a set of blocking operations select one who is ready to input/output and run it.

  The usual pattern of channel processing in go language is to wrap select operation into an endless loop.
 
  Gopher provides similar functionality:

~~~ scala
async[Future]{
  while(!done)
    select {
      case i:channelA.read => ..do-something-with-i
      case ch:channelB.read .. do-something-with-b
    }
}
~~~
   
  Here we read in the loop from channelA or channelB. 

  select accepts partial functions syntax, left parts in `case` clauses must have the following form
   
  * `v:channel.read` (for reading from channel) 
  * `v:channel.write if (v==expr)` (for writing `expr` into channel).
  * `v:Time.after if (v==expr)` (for timeouts).


  Inside case actions, we can use blocking read/writes and await operations.  
  
  Example: 

~~~ scala
TODO
~~~

   A combination of variable and select loop better modeled with help 'fold over select' construction:

~~~ scala
TODO
~~~


   More than one variables in state can be modeled with partial function case syntax:

~~~ scala
val fib = select.fold((0,1)) { case ((x,y), s) =>
    s match {
      case x:channel.write => (y,y+x)
      case q:quit.read => select.exit((x,y))
    }
}
~~~

   Also, we can use 'map over select' to represent results of handling of different events as input side of a channel:

~~~ scala
val multiplexed = select amap {
   case x:ch1.read => (s1,x)
   case y:ch2.read => (s2,y)
 } 
~~~


## Done signals. 

  Sometimes it is useful to receive a message when some `Input` becomes closed. Such inputs are named 'CloseableInputs' and provides a way to receive close notification in selector using `done` pseudo-type.

~~~ scala
 while(!done) 
   select{
     case x:ch.read => Console.println(s"received: ${x}")
     case _:ch.done => Console.println(s"done")
                       done = true
   }
~~~

 Note, that you must exit from current flow in `done` handler, otherwise `done` signals will be intensively generated in a loop.


## Effected{Input,Output,Channel}

  One useful programming pattern, often used in CSP-style programming: have a channel from wich we read (or to where we write) as a part of a state.  In Go language, this is usually modelled as a mutable variable, changed inside the same select statement, where one is read/written.

  In scala-gopher, we have the ability to use a technique of 'EffectedChannel', which can be seen as an entity, which holds channel, can be used in read/write and can be changed only via effect (operation, which accepts the previous state and returns the next).

Let's look at the example:

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


