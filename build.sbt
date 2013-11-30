
name:="scala-gopher"

organization:="com.github.rssh"

scalaVersion := "2.10.3"

resolvers += Resolver.sonatypeRepo("snapshots")

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

autoCompilerPlugins := true

addCompilerPlugin("org.scala-lang.plugins" % "macro-paradise" % "2.0.0-SNAPSHOT" cross CrossVersion.full )

scalacOptions ++= Seq("-unchecked","-deprecation" /* ,"-Ymacro-debug-lite" */  )

libraryDependencies += "org.scala-lang" % "scala-reflect" % "2.10.3"

libraryDependencies += "org.scala-lang.modules" %% "scala-async" % "0.9.0-SNAPSHOT"

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.2.0"

libraryDependencies += "ch.qos.logback" % "logback-core" % "1.0.13"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.13"

libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.5"


//
testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-n", "Now")

version:="0.9.1"


publishMavenStyle := true

publishTo <<= version { (v: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT")) 
    Some("snapshots" at nexus + "content/repositories/snapshots") 
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
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


