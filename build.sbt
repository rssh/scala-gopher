//val dottyVersion = "3.0.0-RC2-bin-SNAPSHOT"
val dottyVersion = "3.0.0"
//val dottyVersion = dottyLatestNightlyBuild.get

ThisBuild/version := "2.0.4-SNAPSHOT"
ThisBuild/versionScheme := Some("semver-spec")

val sharedSettings = Seq(
    organization := "com.github.rssh",
    scalaVersion := dottyVersion,
    name := "scala-gopher",
    resolvers += "Local Ivy Repository" at "file://"+Path.userHome.absolutePath+"/.ivy2/local",
    libraryDependencies += "com.github.rssh" %%% "dotty-cps-async" % "0.8.1",
    libraryDependencies += "org.scalameta" %%% "munit" % "0.7.26" % Test,
    testFrameworks += new TestFramework("munit.Framework")
)

lazy val root = project
  .in(file("."))
  .aggregate(gopher.js, gopher.jvm)
  .settings(
    Sphinx / sourceDirectory := baseDirectory.value / "docs",
    git.remoteRepo := "git@github.com:rssh/scala-gopher.git",
    publishArtifact := false,
  ).enablePlugins(GhpagesPlugin)
  


lazy val gopher = crossProject(JSPlatform, JVMPlatform)
    .in(file("."))
    .settings(sharedSettings)
    .disablePlugins(SitePlugin)
    .jvmSettings(
        scalacOptions ++= Seq( "-unchecked", "-Ycheck:macros", "-uniqid", "-Xprint:types" ),
    ).jsSettings(
        libraryDependencies += ("org.scala-js" %%% "scalajs-java-logging" % "1.0.0").cross(CrossVersion.for3Use2_13),
        // TODO: switch to ModuleES ?
        scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
        scalaJSUseMainModuleInitializer := true,
    )

