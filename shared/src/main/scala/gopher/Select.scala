package gopher

import cps._

import scala.quoted._

class Select[F[_]:CpsSchedulingMonad](api: Gopher[F]):

    inline def apply[A](inline pf: PartialFunction[Any,F[A]]): F[A] =
      ${  Select.onceImpl[F,A]('pf)  }    

object Select:

  def onceImpl[F[_]:Type, A:Type](pf: Expr[PartialFunction[Any,F[A]]])(using Quotes): Expr[F[A]] =
    ???
    


    