package gopher.channels.naive

import gopher.channels._

trait NaiveTie extends Tie[NaiveChannelsAPI] {

  type API = NaiveChannelsAPI
  
  val api = NaiveChannelsAPI
  
}