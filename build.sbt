//val dottyVersion = "3.0.0-RC2-bin-SNAPSHOT"
val dottyVersion = "3.0.0-RC1"
//val dottyVersion = dottyLatestNightlyBuild.get


val sharedSettings = Seq(
    version := "2.0.0-SNAPSHOT",
    organization := "com.github.rssh",
    scalaVersion := dottyVersion,
    name := "scala-gopher",
    resolvers += "Local Ivy Repository" at "file://"+Path.userHome.absolutePath+"/.ivy2/local",
    libraryDependencies += "com.github.rssh" %%% "dotty-cps-async" % "0.4.0",
    libraryDependencies += "org.scalameta" %%% "munit" % "0.7.22" % Test,
    testFrameworks += new TestFramework("munit.Framework")
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
        scalacOptions ++= Seq( "-unchecked", "-Ycheck:macros" ),
    ).jsSettings(
        libraryDependencies += ("org.scala-js" %%% "scalajs-java-logging" % "1.0.0").withDottyCompat(scalaVersion.value),
        // TODO: switch to ModuleES ?
        scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
        scalaJSUseMainModuleInitializer := true,
    )

