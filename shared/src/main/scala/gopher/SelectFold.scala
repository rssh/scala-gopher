package gopher

/**
 * Helper namespace for Select.Fold return value
 * @see [Select.fold]
 **/
object SelectFold:

  /**
   * return value in Select.Fold which means that we should stop folding 
   **/
  case class Done[S](s: S)
  