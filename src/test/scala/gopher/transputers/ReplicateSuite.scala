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


    loop {
      case x: in.read =>
                 System.err.println(s"testDupper, replica: ${replica} received ${x} from ${in.v}")
                 // TODO: implement gopherApi.time.wait
                 Thread.sleep(1000)
                 out.write(x)
    }

}



class ReplicateSuite extends FunSuite
{

  test(" define PortAdapter for TestDupper by hands", Now) {
    // this must be the same as macros-generated,
    import PortAdapters._
    class ReplicatedTestDupper(api:GopherAPI, n:Int) extends ReplicatedTransputer[TestDupper, ReplicatedTestDupper](api,n) {
       val in = new InPortWithAdapter[Int](InPort())
       val out = new OutPortWithAdapter[Int](OutPort())

       def replicatePorts():IndexedSeq[ForeverSelectorBuilder=>Unit] =
       {
         var selectorFuns = IndexedSeq[ForeverSelectorBuilder=>Unit]()

         val (replicatedIns, optInSelectorFun) = in.adapter(in.v,n,api)
         for((rin,e) <- (replicatedIns zip replicatedInstances)) {
               e.in.connect(rin)
         }
         selectorFuns = selectorFuns ++ optInSelectorFun
 
         val (replicatedOuts, optOutSelectorFun) = out.adapter(out.v,n,api)
         for((rout,e) <-  (replicatedOuts zip replicatedInstances)) {
              e.out.connect(rout)
         }
         selectorFuns = selectorFuns ++ optInSelectorFun

         selectorFuns
       }

       def init(): Unit =
       {
         replicatedInstances = (1 to n) map (i => {
              val x = gopherApi.makeTransputer[TestDupper]
              x.replicaNumber = i
              x
         })
        
         val selectorFuns = replicatePorts
         childs = (selectorFuns map(new SelectorRunner(_))) ++ replicatedInstances
         for(x <- childs) x.parent = Some(this)
       }

    }
    val r = new ReplicatedTestDupper(gopherApi,10);
    //( r.in.distribute( (_ % 37 ) ).
    //    out.share()
    //)
    val inChannel = gopherApi.makeChannel[Int](10); 
    val outChannel = gopherApi.makeChannel[Int](10); 
    r.in.connect(inChannel)
    r.out.connect(outChannel)
    val f0 = r.start()
    import scala.concurrent.ExecutionContext.Implicits.global
    var r1=0
    var r2=0
    val beforeF1 = System.currentTimeMillis
    var afterF1 = 0L
    val f1 = go{
      inChannel.write(1)  
      inChannel.write(2)  
      r1 = outChannel.read
      r2 = outChannel.read
      afterF1 = System.currentTimeMillis
    }
    Await.ready(f1, 2 seconds)
    assert(afterF1!=0L)
    assert((afterF1 - beforeF1) < 2000)
    r.stop()
  }


  test("WordCount must be replicated") {
    pending
    import PortAdapters._
    //val t1 = Replicate[WordCountTestTransputer](_.inS.distribute{ case(id,w) => id.toInt })
  }


  def gopherApi = CommonTestObjects.gopherApi

}

