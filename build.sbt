
name:="scala-go"

organization:="com.github.rssh"

scalaVersion := "2.10.1"

resolvers += Resolver.sonatypeRepo("snapshots")

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

autoCompilerPlugins := true

libraryDependencies += compilerPlugin("org.scala-lang.plugins" % "continuations" % "2.10.1")

scalacOptions ++= Seq("-unchecked","-deprecation","-P:continuations:enable")

libraryDependencies += "org.scala-lang" % "scala-reflect" % "2.10.1"

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.1.2"

version:="0.0.1-SNAPSHOT"

