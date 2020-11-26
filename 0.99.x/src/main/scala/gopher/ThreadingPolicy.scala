package gopher

sealed trait ThreadingPolicy

object ThreadingPolicy
{
  case object Single extends ThreadingPolicy
  case object Multi extends ThreadingPolicy
}

