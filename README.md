  
## Gopher: asynchronous implementation of go-like channels/selectors in scala
=======

### Dependences:    
 
For scala 3.1.1+:

    libraryDependencies += "com.github.rssh" %% "scala-gopher" % "3.0.1"

For scala 3 and 3.1.0:

    libraryDependencies += "com.github.rssh" %% "scala-gopher" % "2.1.0"

Note, that 3.0.x have no new functionality agains 2.1.0 but need to be a next major release because of binary incompability caused by difference between dotty-cps-async-0.9.5 and 0.9.7.

For scala2: 

    libraryDependencies += "com.github.rssh" %% "scala-gopher" % "0.99.15"

(For 0.99.x documentation look at README at 0.99x branch: https://github.com/rssh/scala-gopher/tree/0.99x)
The main differences between 0.99 and 2.0.0 is described in https://github.com/rssh/scala-gopher/blob/master/docs/changes-2.0.0.md

Scala-gopher is open source (license is Apache2); binaries are available from the maven-central repository.

## Overview

   Scala-gopher is a scala library, build on top of dotty-cps-async, which provide an implementation of 
 CSP [Communicate Sequential Processes] primitives, known as 'Go-like channels.'  

Note, which this is not an emulation of go language structures in Scala, but rather a reimplementation of the main ideas in 'scala-like' manner.
  

### Initialization
 
 You need a given of gopherApi for creating channels and selectors.  
 
      import gopher._
      import cps._
  
      import cps.monads.FutureAsyncMonad
      ......
     
      given Gopher[Future]()    
 
 type parameter can be any monad, which should implement CpsSchedulingMonad typeclass.

 

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

Also, you can use only `ReadChannel` or `WriteChannel` interfaces, where an appropriative read/write operations are defined. 
For `ReadChannel`, exists usual stream functions, like `map`, `zip`, `takeN`, `fold` ... etc. 

For example, here is direct translation of golang code:
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

last loop can be repharased in more scala way as:

~~~ scala
val sum = (channel.take(1000)).fold(0)((s,i) => s+i)
~~~


  Here is filtered channel, wich produce prime numbers:

~~~ scala
 def filter0(in:Channel[Future,Int,Int]):ReadChannel[Future,Int] =
    val filtered = makeChannel[Int]()
    var proxy: ReadChannel[Future, Int] = in;
    async {
      while(true) {
          val prime = proxy.read()
          proxy = proxy.filter(_ % prime != 0)
          filtered.write(prime)
      } 
    }
    filtered
~~~
  
(less imperative way to do the same, described later in `select.fold`).


Any Iterable can be represented as `ReadChannel` via extension method `asReadChannel`. 
 
Also, we can use Scala futures as channels, which produce one value and then closes. For obtaining such input use  `gopherApi.futureInput`.

`|` (i.e. or) operator used for merged inputs, i.e. `(x|y).read` will read a value from channel x or y when one will be available.

Also, note that you can provide own ReadChannel and WriteChannel implementations by implementing ```addReader/addWriter``` methods.


## Select loops and folds

  'select statement' is somewhat similar to Unix 'select' syscall:
  from a set of blocking operations select one who is ready to input/output and run it.

  The usual pattern of channel processing in go language is to wrap select operation into an endless loop.

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

~~~ scala
async{
        var done = false
        while(!done) {
          select {
            case x: ch.read => 
                sum = sum+x
                if (x > 100) {
                  done = true
                } 
          }
        }
}
~~~
   
 select.loop can be used for less imperative code organization:

~~~ scala
async{
    select.loop{
         case x: ch.read => 
               sum = sum+x
               (x <= 100)
    }
}
~~~

  Here, the branch inside select should return true or false.  If true -- loop will be continued, if false - finished.

 
  select.fold (or afold - as variant which is alredy wrapped in async) provide an abstraction for iterating over set of
 events by applying function to state:

~~~ scala
def filter1(in:Channel[Future,Int,Int]):ReadChannel[Future,Int] =
   val q = makeChannel[Int]()
   val filtered = makeChannel[Int]()
   select.afold(in){ ch => 
     select{
       case prime: ch.read => 
                filtered.write(prime)
                ch.filter(_ % prime != 0)
     }
   }
   filtered
~~~

 The argument to the fold function is state.
 Function should or produce next state (as in `filter1` example) or produce special value SelectFold.Done(x):


~~~ scala
val fib = select.fold((0,1)) { case (x,y) =>
    select{
      case x:channel.write => (y,y+x)
      case q:quit.read => SelectFold.Done((x,y))
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

  Sometimes it is useful to receive a message when some `ReadChannel` becomes closed. Exists a way to receive close notification in selector using `done` pseudo-channel, which is available for each 'normal' channel. When the channel is closed, all readers of done channels receive notifications.

~~~ scala
 while(!done) 
   select{
     case x:ch.read => Console.println(s"received: ${x}")
     case _:ch.done.read => Console.println(s"done")
                       done = true
   }
~~~

  Note, that if we query some channel and it's done channel in the same select, and done channel is not aliased in some vairable, then done handler will be called first after channel close. 


# References:
----------------------

## 2.0.x implementation
* source code: https://github.com/rssh/scala-gopher
* scaladoc: https://rssh.github.io/scala-gopher/api/jvm/index.html

## [0.99.x] implementation:
* source code: https://github.com/rssh/scala-gopher/tree/0.99x
* presentations: 
     * Odessa Java/Scala Labs; Kiev Scala Meetup: Oct. 2014: http://www.slideshare.net/rssh1/scala-gopher2014  
     * Wix R&D meetup. Mart 2016: http://www.slideshare.net/rssh1/csp-scala-wixmeetup2016
     * Scala Symposium. Oct. 2016. Amsterdam.  http://www.slideshare.net/rssh1/scalagopher-cspstyle-programming-techniques-with-idiomatic-scala
* techreport: https://arxiv.org/abs/1611.00602


##  CSP-Related links:
* [Communicating Sequential Processes book by Tony Hoare](http://www.usingcsp.com)
* [brief history of CSP in Bell-labs](http://swtch.com/~rsc/thread/)
    


