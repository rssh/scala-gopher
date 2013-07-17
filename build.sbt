
name:="scala-go"

organization:="com.github.rssh"

scalaVersion := "2.10.2"

resolvers += Resolver.sonatypeRepo("snapshots")

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

autoCompilerPlugins := true

scalacOptions ++= Seq("-unchecked","-deprecation" /*,"-Ymacro-debug-lite"*/ )

libraryDependencies += "org.scala-lang" % "scala-reflect" % "2.10.2"

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.2.0"

libraryDependencies += "com.typesafe.akka" %% "akka-channel" % "2.2.0"


version:="0.0.1-SNAPSHOT"

