package gopher.transputers

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

sealed trait OverflowMessage
case object UsersOverflow extends OverflowMessage
case class WordsOverflow(userId: Long) extends OverflowMessage

trait WordCountTestTransputer extends SelectTransputer
{

  val inS = InPort[(Long,String)]()
  val control = InPort[ControlMessage]()

  val topOut = OutPort[(Long,Seq[(String,Int)])]()
  val overflows = OutPort[OverflowMessage]()

  @volatile var data = Map[Long,Map[String,Int]]()
  @volatile var maxWords : Int = 100
  @volatile var maxUsers : Int = 100

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
                 log.info(s"testDupper, replica: ${replica} received ${x} from ${in.v}")
                 // TODO: implement gopherApi.time.wait
                 Thread.sleep(1000)
                 out.write(x)
                 nProcessedMessages += 1
    }

}



class ReplicateSuite extends FunSuite
{

  test(" define replication of TestDupper with port adapters", Now) {
    val r = gopherApi.replicate[TestDupper](10)
    import PortAdapters._
    ( r.in.distribute( (_ % 37 ) ).
        out.share()
    )
    val inChannel = gopherApi.makeChannel[Int](10); 
    val outChannel = gopherApi.makeChannel[Int](10); 
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
    Await.ready(f1, 5 seconds)
    assert(r.replicated.map(_.nProcessedMessages).sum == 2)
    assert(r.replicated.forall(x => x.nProcessedMessages == 0 || x.nProcessedMessages == 1))
    r.stop()
  }


  test("WordCount must be replicated") {
    pending
    import PortAdapters._
    //val t1 = Replicate[WordCountTestTransputer](_.inS.distribute{ case(id,w) => id.toInt })
  }


  def gopherApi = CommonTestObjects.gopherApi

}

