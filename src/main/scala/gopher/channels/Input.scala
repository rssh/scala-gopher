package gopher.channels

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.experimental.macros
import scala.language.reflectiveCalls
import scala.reflect.macros.blackbox.Context
import scala.reflect.api._
import scala.util._
import java.util.concurrent.ConcurrentLinkedQueue

import gopher._
import gopher.util._


import java.util.concurrent.atomic._

/**
 * Entity, from which we can read objects of type A.
 *
 *
 */
trait Input[A]
{

/*
  def  aread:Future[A] = ???

  def  read:A = ???

  def atake(n:Int):Future[IndexedSeq[A]] = ???

  def filter(p: A=>Boolean): Input[A] = ???
*/

}


