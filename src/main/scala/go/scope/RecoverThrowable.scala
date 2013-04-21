package go.scope


class RecoverThrowable[A](val retval: A, val sc: ScopeContext[A]) extends Throwable
