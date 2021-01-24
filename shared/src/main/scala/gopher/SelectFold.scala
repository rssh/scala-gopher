package gopher

object SelectFold:

  case class Done[S](s: S)
  