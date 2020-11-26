//val dottyVersion = "3.0.0-M1-bin-20201022-b26dbc4-NIGHTLY"
val dottyVersion = "3.0.0-RC1-bin-SNAPSHOT"
//val dottyVersion = dottyLatestNightlyBuild.get


val sharedSettings = Seq(
    version := "2.0.0-SNAPSHOT",
    organization := "com.github.rssh",
    scalaVersion := dottyVersion,
    name := "scala-gopher",
    //resolvers += "Local Ivy Repository" at "file://"+Path.userHome.absolutePath+"/.ivy2/local",
    resolvers += "Local Ivy Repository" at "file://"+Path.userHome.absolutePath+"/.ivy2/local",
    libraryDependencies += "com.github.rssh" %%% "dotty-cps-async" % "0.3.4-SNAPSHOT",
)

lazy val root = project
  .in(file("."))
  .aggregate(gopher.js, gopher.jvm)
  .settings(
    Sphinx / sourceDirectory := baseDirectory.value / "docs",
    git.remoteRepo := "git@github.com:rssh/dotty-cps-async.git",
    publishArtifact := false,
  ).enablePlugins(SphinxPlugin)
   .enablePlugins(GhpagesPlugin)


lazy val gopher = crossProject(JSPlatform, JVMPlatform)
    .in(file("."))
    .settings(sharedSettings)
    .disablePlugins(SitePlugin)
    .jvmSettings(
        scalacOptions ++= Seq( "-unchecked" ), 
        libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test",
    ).jsSettings(
        scalaJSUseMainModuleInitializer := true,
        libraryDependencies += ("org.scala-js" %% "scalajs-junit-test-runtime" % "1.2.0" % Test).withDottyCompat(scalaVersion.value)
    )

