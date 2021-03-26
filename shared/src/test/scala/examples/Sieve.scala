package examples

import scala.concurrent.{Channel=>_,_}
import cps._
import cps.monads.FutureAsyncMonad
import gopher._

import scala.concurrent.ExecutionContext.Implicits.global



object Sieve {

   given Gopher[Future] = SharedGopherAPI.apply[Future]()


   def run(in: Channel[Future,Int, Int], out: Channel[Future,Int,Int]): Future[Unit] = async[Future] {
      var middle: ReadChannel[Future,Int] = in
      while (!in.isClosed) {
         val x = middle.read()
         out.write(x)
         middle = middle.filter(_ % x != 0)
      }
   }



   def runRec(in: Channel[Future,Int, Int], out: Channel[Future,Int, Int]): Future[Unit] = async[Future] {
       val x = in.read()
       out.write(x)
       await(runRec(in.filter(_ % x != 0), out))
   }

   
   def runFold(in:Channel[Future,Int, Int], out: Channel[Future,Int, Int]) = async {  // ???


       select.fold(in){ (s, g) =>
            val x = s.read()
            out.write(x)
            s.filter(_ % x != 0)
       }
   }


}
