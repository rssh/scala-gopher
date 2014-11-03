
name:="scala-gopher-compiler-issue"

organization:="com.github.rssh"

scalaVersion := "2.11.4"

resolvers += Resolver.sonatypeRepo("snapshots")

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

scalacOptions ++= Seq("-unchecked","-deprecation" /* ,"-Ymacro-debug-lite" */  )

libraryDependencies += "org.scala-lang" % "scala-reflect" % "2.11.4"


version:="0.99.3-SNAPSHOT"




