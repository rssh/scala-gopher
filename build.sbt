
name:="scala-gopher-compile-forever-example"

organization:="com.github.rssh"

scalaVersion := "2.11.8"


resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

scalacOptions ++= Seq("-unchecked","-deprecation", "-feature" , "-Ydebug" /*,  "-Ymacro-debug-lite" ,  "-Ylog:lambdalift" */ )

libraryDependencies <+= scalaVersion( "org.scala-lang" % "scala-reflect" % _ )

libraryDependencies += "org.scala-lang.modules" %% "scala-async" % "0.9.5"

//libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "test"

//libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.2"

//TODO: enable after 1.0
//libraryDependencies += "com.typesafe.akka" %% "akka-stream-experimental" % "0.9"

//testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-n", "Now")

version:="0.99.7-SNAPSHOT"


