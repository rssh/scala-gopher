package gopher.scope

import scala.annotation.tailrec
import scala.util._
import scala.reflect.runtime.universe.{Try => _, _}
import scala.io._
import gopher._

import org.scalatest._

trait Source
{
   def name(): String
   def lines(): Iterator[String]
   def close(): Unit
}

object TestParser
{

 def parseCsv(source: Source): Either[String, Seq[Seq[Double]]] = 
   withDefer[Either[String,Seq[Seq[Double]]]]{ d =>
     d.defer{ 
       if (!d.recover {
            case ex: Throwable => Left(ex.getMessage)
          })
          source.close() 
     }
     val retval:Either[String,Seq[Seq[Double]]] = Right{
         for( (line, nLine) <- source.lines.toList zip Stream.from(1) ) yield withDefer[Seq[Double]] { d =>
            line.split(",") map { s=> 
                                  d.defer{
                                   d.recover{
                                      case ex: NumberFormatException =>
                                        throw new RuntimeException(s"parse error in line ${nLine} file ${source.name} ")
                                   }
                                  }
                                  s.toDouble 
                                }
         }.toSeq
     }
     retval
 }

}


class DefersSuite extends FunSuite
{

  test("Defers.parseCsv: reading from unexistent file will return failure with FileNotFoundException") {

    val s = new Source {
                          def name()="unexistent.txt"
                          def lines()=source.getLines
                          def close()=source.close()
                          lazy val source = scala.io.Source.fromFile(name)
                       } 
    TestParser.parseCsv(s) match {
           case Right(x) => assert(false,"unexistent source parsed")
           case Left(s) =>  assert(s.contains("file"))
    }

  }

  test("Defers.parseCsv: error in second string must be discovered") {
    val s = new Source {
                          def name()="internal"
                          def lines()=Seq(
                            "1,3,4,5,6.0,8,9",
                            "3,4,5,6,xxxx7.0,8,9"
                          ).iterator
                          def close(): Unit = {}
                       }

    TestParser.parseCsv(s) match {
           case Right(x) => assert(false,"source with error parsed")
           case Left(s) =>  assert(s.contains("2"))
    }

  }


}

