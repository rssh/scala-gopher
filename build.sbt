
name:="scala-gopher"

organization:="com.github.rssh"

scalaVersion := "2.11.2"

resolvers += Resolver.sonatypeRepo("snapshots")

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

scalacOptions ++= Seq("-unchecked","-deprecation" /* ,"-Ymacro-debug-lite" */  )

libraryDependencies += "org.scala-lang" % "scala-reflect" % "2.11.2"

libraryDependencies += "org.scala-lang.modules" %% "scala-async" % "0.9.2"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.0" % "test"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.5"

//libraryDependencies += "ch.qos.logback" % "logback-core" % "1.0.13"

//libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.13"

//libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.5"



testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-n", "Now")

version:="0.9.1"


publishMavenStyle := true

publishTo <<= version { (v: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT")) 
    Some("snapshots" at nexus + "content/repositories/snapshots") 
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}


publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>http://rssh.github.com/scala-gopher</url>
  <licenses>
    <license>
      <name>Apache 2</name>
      <url>pt</url>
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


