package gopher.transputers

import scala.language._
import gopher._
import gopher.channels._
import gopher.util._
import gopher.tags._
import org.scalatest._
import scala.concurrent._
import scala.concurrent.duration._
import akka.actor._

sealed trait ControlMessage
case class SetMaxWords(n:Int) extends ControlMessage
case class SetMaxUsers(n:Int) extends ControlMessage
case class SendTopWords(userId: Long, nWords:Int) extends ControlMessage
case object Clear extends ControlMessage
case object Stop extends ControlMessage

sealed trait OverflowMessage
case object UsersOverflow extends OverflowMessage
case class WordsOverflow(userId: Long) extends OverflowMessage

trait WordCountTestTransputer extends SelectTransputer
{

  val inS = InPort[(Long,String)]()
  val control = InPort[ControlMessage]()

  val topOut = OutPort[(Long,Seq[(String,Int)])]()
  val overflows = OutPort[OverflowMessage]()

  var data = Map[Long,Map[String,Int]]()
  var maxWords : Int = 100
  var maxUsers : Int = 100

  loop {
    case x : inS.read @unchecked => 
               val (id, word) = x
               val nWords = updateData(id, word )
               if (data.size > maxUsers) {
                   overflows.write(UsersOverflow)
               }
               if (nWords > maxWords) {
                   overflows.write(WordsOverflow(id))
               }
    case c: control.read =>
             c match {
               case SetMaxWords(n) => maxWords=n
               case SetMaxUsers(n) => maxUsers=n
               case SendTopWords(userId, nWords) =>
                              topOut.write((userId,topNWords(userId, nWords)))
               case Clear => data = Map()
               case Stop => stop()
             }
                         
  }

  def updateData(userId: Long, word: String): Int =
   data.get(userId) match {
      case Some(m) => val newM = updateWordCount(m,word) 
                      data = data.updated(userId, newM)
                      newM.size
      case None => data = data.updated(userId,Map(word -> 1))
                      1
   }

  def updateWordCount(m:Map[String,Int],w:String): Map[String,Int] =
    m.updated(w,
       m.get(w) match {
        case Some(n) => n+1
        case None    => 1
      }
    )

  def topNWords(userId:Long, nWords: Int): Seq[(String,Int)] =
   data.get(userId) match {
     case Some(m) => m.toSeq.sortBy{ case (w1,n1) => -n1 }.take(nWords)
     case None => List()
   }

}

trait TestDupper extends SelectTransputer with TransputerLogging
{

    val in = InPort[Int]()

    val out = OutPort[Int]()

    @volatile var nProcessedMessages = 0

    loop {
      case x: in.read =>
                 log.info(s"testDupper, replica: ${replica} received ${x} from ${in}")
                 // TODO: implement gopherApi.time.wait
                 Thread.sleep(1000)
                 out.write(x)
                 nProcessedMessages += 1
    }

}



class ReplicateSuite extends FunSuite
{

  test(" define replication of TestDupper with port adapters") {
    val r = gopherApi.replicate[TestDupper](10)
    import PortAdapters._
    ( r.in.distribute( (_ % 37 ) ).
        out.share()
    )
    val inChannel = gopherApi.makeChannel[Int](10)
    val outChannel = gopherApi.makeChannel[Int](10)
    r.in.connect(inChannel)
    r.out.connect(outChannel)
    val f0 = r.start()
    import scala.concurrent.ExecutionContext.Implicits.global
    var r1=0
    var r2=0
    val beforeF1 = System.currentTimeMillis
    val f1 = go{
      inChannel.write(1)  
      inChannel.write(2)  
      r1 = outChannel.read
      r2 = outChannel.read
    }
    Await.ready(f1, 10 seconds)
    assert(f1.isCompleted)
    assert(r.replicated.map(_.nProcessedMessages).sum == 2)
    assert(r.replicated.forall(x => x.nProcessedMessages == 0 || x.nProcessedMessages == 1))
    r.stop()
  }


  test("WordCount must be replicated and accessbke via *! ports side") {
    //pending
    import PortAdapters._
    val nReplics = 2
    val t = gopherApi.replicate[WordCountTestTransputer](nReplics).inS.distribute{ case(id,w) => id.toInt }.control.duplicate()
    val ft = t.start()
    val topIn: Input[(Long,Seq[(String,Int)])] = t.topOut.*!
    @volatile var nReceived = 0
    val of = gopherApi.select.forever {
                 case x: topIn.read @ unchecked =>
                          //Console.println("received:"+x)
                          nReceived = nReceived + 1
                          if (nReceived == nReplics) {
                             implicitly[FlowTermination[Unit]].doExit(())
                          }
                 case o: OverflowMessage if (o==(t.overflows*!).read) =>
                          Console.println("overflow received:"+o)
              }
    val outS: Output[(Long,String)] = t.inS.*!
    //  stack overflow in compiler. [2.11.4]
    //val fw = go { 
    //   outS.writeAll( "Some nontrivial sentence with more than one word".split(" ").toList map ((11L,_))  )
    //}
    import scala.concurrent.ExecutionContext.Implicits.global
    val fw = outS.awriteAll(
               "Some nontrivial sentence with more than one word".split(" ").toList map ((1L,_))  
             ) flatMap ( _ =>
                outS.awriteAll( "And in next text word 'word' will be one of top words".split(" ").toList map ((1L,_))  
                              )  flatMap ( _ =>
                  outS.awriteAll( "One image is worse than thousand words".split(" ").toList map ((1L,_))  
               ) ) ) flatMap { _ =>
                 t.control.*! awrite SendTopWords(1L, 3)
              }
    Await.ready(fw, 10 seconds)
    t.stop()
    Await.ready(ft, 10 seconds)
    Await.ready(of, 10 seconds)
    assert(nReceived == nReplics)
  }


  def gopherApi = CommonTestObjects.gopherApi

}

