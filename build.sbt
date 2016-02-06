
name:="scala-gopher"

organization:="com.github.rssh"

scalaVersion := "2.11.7"

resolvers += Resolver.sonatypeRepo("snapshots")

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

scalacOptions ++= Seq("-unchecked","-deprecation", "-feature" /*, "-Ymacro-debug-lite" , "-Ydebug"  , "-Ylog:lambdalift" */ )

libraryDependencies <+= scalaVersion( "org.scala-lang" % "scala-reflect" % _ )

libraryDependencies += "org.scala-lang.modules" %% "scala-async" % "0.9.5"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "test"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.1"

//TODO: enable after 1.0
//libraryDependencies += "com.typesafe.akka" %% "akka-stream-experimental" % "0.9"

//testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-n", "Now")

version:="0.99.7-SNAPSHOT"

// disable publish to sonatype from community build:
//publishMavenStyle := true
//
//publishTo <<= version { (v: String) =>
//  val nexus = "https://oss.sonatype.org/"
//  if (v.trim.endsWith("SNAPSHOT")) 
//    Some("snapshots" at nexus + "content/repositories/snapshots") 
//  else
//    Some("releases" at nexus + "service/local/staging/deploy/maven2")
//}


publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>http://rssh.github.com/scala-gopher</url>
  <licenses>
    <license>
      <name>Apache 2</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:rssh/scala-gopher.git</url>
    <connection>scm:git:git@github.com:rssh/scala-gopher.git</connection>
  </scm>
  <developers>
    <developer>
      <id>rssh</id>
      <name>Ruslan Shevchenko</name>
      <url>rssh.github.com</url>
    </developer>
  </developers>
)


