package gopher.scope


class RecoverThrowable[A](val retval: A, val sc: ScopeContext) extends Throwable
