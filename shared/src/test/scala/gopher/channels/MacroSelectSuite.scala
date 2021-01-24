package gopher.channels

import cps._
import gopher._
import munit._

import scala.concurrent.{Channel=>_,_}
import scala.concurrent.duration._
import scala.util._
import scala.language.postfixOps

import cps.monads.FutureAsyncMonad

class MacroSelectSuite extends FunSuite
{

    import ExecutionContext.Implicits.global
    given Gopher[Future] = SharedGopherAPI.apply[Future]()

    
    test("select emulation with macroses")  {

        val channel = makeChannel[Int](100)

        async[Future] {
            var i = 1
            while(i <= 1000) {
                channel <~ i
                i+=1
            }
            //TODO: implement for in goas preprocessor to async
            // dotty bug: position not set
            //for( i <- 1 to 1000)
            //  channel <~ i
        }

        var sum = 0
        val consumer = async[Future] {
            select.loop{
              case i: channel.read =>
                //System.err.println("received:"+i)
                sum = sum + i
                i < 1000
            }
            sum
        }

        for{
            _ <- consumer
            xsum = (1 to 1000).sum
        } yield assert(xsum == sum)

    }

    
    test("select with run-once")  {
        val channel1 = makeChannel[Int](100)
        val channel2 = makeChannel[Int](100)

        val g = async[Future] {
            var nWrites=0
            select{
                case x: channel1.write if (x==1) => {
                                nWrites = nWrites + 1
                    }
                case x: channel2.write if (x==1) => {
                                nWrites = nWrites + 1
                    }
            }

            var nReads=0
            select {
                    case  x: channel1.read => { {}; nReads = nReads + 1 }
                    case  x: channel2.read => { {}; nReads = nReads + 1 }
            }

            (nWrites, nReads)
        }

        g map { case(nWrites,nReads) => assert(nWrites==1 && nReads==1)}

    }

    
    test("select from futureInput") {
        async[Future] {
            val channel = makeChannel[Int](100)
            val future = Future successful 10
            val fu = futureInput(future)
            var res = 0
            val r = select{
                case x: channel.read =>
                    Console.println(s"readed from channel: ${x}")
                    true
                case x: fu.read =>
                    //Console.println(s"readed from future: ${x}")
                    res = x
                    false
                //  syntax for using channels/futures in cases without
                //  setting one in stable identifers.
                //case x: Int if (x == future.read) =>
                //    res = x
            }
            assert(res == 10)
        }
    }

    
    /*
     TODO: think, are we want to keep this syntax in 2.0.0 (?)
    test("select syntax with read/writes in guard")  {
        import gopherApi._
        val channel1 = makeChannel[Int](100)
        val channel2 = makeChannel[Int](100)
        var res = 0
        val r = select.loop{
            case x: Int if (x==channel1.write(3)) =>
                Console.println(s"write to channel1: ${x} ")
                true
            case x: Int if (x==channel2.read) =>
                Console.println(s"readed from channel2: ${x}")
                true
            case x: Int if (x==(Future successful 10).read) =>
                res=x
                false
        }
        r map (_ => assert(res==10))
    }
    */
    


    test("select syntax with @unchecked annotation")  {
        val channel1 = makeChannel[List[Int]](100)
        val channel2 = makeChannel[List[Int]](100)
        var res = 0
        channel1.awrite(List(1,2,3))
        async {
            select.once{
                case x: channel1.read @ unchecked =>
                    res=1
                case x: channel2.read @ unchecked =>
                    res=2
            }
            assert(res==1)
        }
        
    }

    
    test("tuple in caseDef as one symbol")  {
        async {
            val ch = makeChannel[(Int,Int)](100)
            var res = 0
            ch.awrite((1,1))
            val r = select.once{
                case xpair: ch.read @unchecked  =>
                    // fixed error in compiler: Can't find proxy
                    val (a,b)=xpair
                    res=a
            }
            assert(res == 1)
        }
    }

    
    test("multiple readers for one write")  {
        val ch = makeChannel[Int](10)
        var x1 = 0
        var x2 = 0
        var x3 = 0
        var x4 = 0
        var x5 = 0
        val f1 = async {
            select.once{
                case x:ch.read =>
                    x1=1
            }
        }
        val f2 = async {
            select.once{
                case x:ch.read =>
                    x2=1
            }
        }
        val f3 = async {
            select.once{
                case x:ch.read =>
                    x3=1
            }
        }
        val f4 = async {
            select.once{
                case x:ch.read =>
                    x4=1
            }
        }
        val f5 = async{
            select.once{
                case x:ch.read =>
                    x5=1
            }
        }
        for {_ <- ch.awrite(1)
             _ <- Future.firstCompletedOf(List(f1, f2, f3, f4, f5))
             _ = ch.close()
             _ <- Future.sequence(List(f1, f2, f3, f4, f5)).recover{
                 case _ :ChannelClosedException => ()
             }
        } yield assert(x1+x2+x3+x4+x5==1)
    }

    
    test("fold over selector")  {
            val ch = makeChannel[Int](10)
            val back = makeChannel[Int]()
            val quit = Promise[Boolean]()
            val quitChannel = futureInput(quit.future)
            val r = async {
                select.fold(0){ (x,g) =>
                    g.select {
                        case a:ch.read => back <~ a
                            x+a
                        case q: quitChannel.read => 
                            g.done(x)
                    }
                }
            }
            ch.awriteAll(1 to 10)
            back.aforeach{ x =>
                if (x==10) {
                    quit success true
                }
            }
            r map (sum => assert(sum==(1 to 10).sum))
    }

    
    test("fold over selector with idle-1")  {
        val ch1 = makeChannel[Int](10)
        val ch2 = makeChannel[Int](10)
        ch1.awrite(1)
        //implicit val printCode = cps.macroFlags.PrintCode
        //implicit val debugLevel = cps.macroFlags.DebugLevel(20)
        for {
            _ <- Future.successful(())
            sf = select.afold((0, 0, 0)) { case ((n1, n2, nIdle), s) =>
                s.select{
                    case x: ch1.read =>
                        val nn1 = n1 + 1
                        if (nn1 > 100) {
                            s.done((nn1, n2, nIdle))
                        } else {
                            ch2.write(x)
                            (nn1, n2, nIdle)
                        }
                    case x: ch2.read =>
                        ch1.write(x)
                        (n1, n2 + 1, nIdle)
                    case t : Time.after if (t == (50 milliseconds)) =>
                        (n1, n2, nIdle + 1)
                }
            } 
            (n1, n2, ni) <- sf
            _ = assert(n1 + n2 + ni > 100)
            sf2 = select.afold((0, 0)) { case ((n1, nIdle), s) =>
                s.select{
                    case x: ch1.read =>
                        (n1 + 1, nIdle)
                    case t: Time.after if t == (50 milliseconds) =>
                        val nni = nIdle + 1
                        if (nni > 3) {
                            s.done((n1, nni))
                        } else {
                            (n1, nni)
                        }
                }
            }
            (n21, n2i) <- sf2
        } yield
           assert(n2i>3)
    }

       
    test("map over selector".only)  {
        val ch1 = makeChannel[Int](10)
        val ch2 = makeChannel[Int](10)
        val quit = Promise[Boolean]()
        val quitChannel = quit.future.asChannel
        val out = select.mapAsync[Int]{ s =>
            val v = async{
                  s.apply{
                    case x:ch1.read => 
                         x*2
                    case Channel.Read(x:Int,ch) if ch == ch2  => 
                         x*3
                    case Channel.Read(q, ch) if ch == quitChannel => 
                         throw ChannelClosedException()
                  }
            }
            v
        }
        ch1.awriteAll(1 to 10)
        ch2.awriteAll(100 to 110)
        val f: Future[Int] = out.afold(0){  (s,x) => s+x }
        async {
            Time.sleep(1 second)
            quit success true
            val x = await(f)
            //println(s"x==$x")
            assert(x > 3000)
        }
    }
    
    


    test("input fold") {
      val ch1 = makeChannel[Int]()
      ch1.awriteAll(1 to 10) map { _ => ch1.close() }
      async {
        val x = ch1.fold(0){ case (s,x) => s+x }
        assert(x==55)
      }
    }


    test("map over selector")  {
     val ch1 = makeChannel[Int]()
     val ch2 = makeChannel[Int](1)
     val f1 = ch1.awrite(1)
     val f2 = ch2.awrite(2)
     async {
        val chs = for(s <- select) yield {
                s.apply{
                  case x:ch1.read => x*3
                  case x:ch2.read => x*5
                }
              }
        val fs1 = chs.aread
        val fs2 = chs.aread
        val s1 = await(fs1)
        val s2 = await(fs2)
        assert(s1==3 || s1==10)
     }
    }
    
   
    
    test("one-time channel make")  {
        val ch = makeOnceChannel[Int]()
        val f1 = ch.awrite(1)
        val f2 = ch.awrite(2)
        async {
            val x = await(ch.aread)
            val x2 = Try(await(f2.failed)) 
            assert(x == 1)
            assert(x2.get.isInstanceOf[ChannelClosedException])
        }
    }
   

   
    test("check for done signal from one-time channel")  {
        val ch = makeOnceChannel[Int]()
        val sf = select.afold((0)){ (x,s) =>
            s.select{
                case v: ch.read => x + v
                case v: ch.done.read => s.done(x)
            }
        }
        val f1 = ch.awrite(1)
        async {
            val r = await(sf)
            assert(r==1)
        }
    } 

   
    test("check for done signal from unbuffered channel")  {
        val ch = makeChannel[Int]()
        val sf = select.afold((0)){ (x,s) =>
            s.select{
                case v: ch.read => x + v
                case v: ch.done.read => s.done(x)
            }
        }
        val f1 = ch.awriteAll(1 to 5) map (_ =>ch.close)
        async {
            val r = await(sf)
            assert(r==15)
        }
    }

   
    test("check for done signal from buffered channel")  {
        val ch = makeChannel[Int](10)
        val sf = select.afold((0)){ (x,s) =>
            s.select {
                case v: ch.read => x + v
                case c: ch.done.read => s.done(x)
            }
        }
        val f1 = async {
            ch.writeAll(1 to 5)
            // let give all buffers to processe
            Time.sleep(200 millis)
            ch.close()
        } 
        async {
            val r = await(sf)
            assert(r == 15)
        }
    }


    test("check for done signal from select map")  {  
        val ch1 = makeChannel[Int]()
        val ch2 = makeChannel[Int]()
        val q = makeChannel[Boolean]()
        async{ 
            val chs: ReadChannel[Future,Int] = for(s <- select) yield {
                s.select{
                    case x: ch1.read => x*3
                    case x: ch2.read => x*2
                    case x: q.read =>
                        throw ChannelClosedException()
                }
            }
            val chs2 = select.afold(0){ (n,s) =>
                s.select{
                    case x:chs.read =>
                        n + x
                    case x:chs.done.read =>
                        s.done(n)
                }
            }
            // note, that if we want call of quit after last write,
            //   ch1 and ch2 must be unbuffered.
            val sendf = for{ _ <- ch1.awriteAll(1 to 10) 
                      _ <- ch2.awriteAll(1 to 10) 
                      _ <- q.awrite(true) } yield 1
            val r = await(chs2)
            assert( r ==  (1 to 10).map(_ * 5).sum + 1)
        }
    }

   
}
