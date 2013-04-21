package go.scope


class PanicException[A](val s: String, val sc: ScopeContext[A]) extends Exception

