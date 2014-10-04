
package gopher

/**
 *
 * == Overview ==
 *
 *<ul>
 * <li> [[Input]] and [[Output]] provide callback-based interfaces for input/output facilities. </li>
 * <li> [[IOChannel]] provide implementation for asynchronics channels processing inside JVM. </li>
 * <li> [[SelectorBuilder]] is a way to create selectors. </li>
 *</ul>
 *
 *
 * == Internals ==
 *
 * Core entity is [[Continuated]] which provide iteratee-like structure for reading and writing.
 * Instance of [[Continuated]] represent one step of computations and leave in queue inside [[ChannelProcessor]] or [[ChannelActor]]
 * [[Selector]] transform [[Continuated]] to executed exclusive with each other within one selector. Also we have [[IdleDetector]] which
 * determinate idle selectors and activer appropriative actions.
 *
 */
package object channels {


}

