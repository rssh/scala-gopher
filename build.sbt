//val dottyVersion = "3.0.0-RC2-bin-SNAPSHOT"
val dottyVersion = "3.0.1"
//val dottyVersion = dottyLatestNightlyBuild.get

ThisBuild/version := "2.0.4"
ThisBuild/versionScheme := Some("semver-spec")

val sharedSettings = Seq(
    organization := "com.github.rssh",
    scalaVersion := dottyVersion,
    name := "scala-gopher",
    resolvers += "Local Ivy Repository" at "file://"+Path.userHome.absolutePath+"/.ivy2/local",
    libraryDependencies += "com.github.rssh" %%% "dotty-cps-async" % "0.9.0",
    libraryDependencies += "org.scalameta" %%% "munit" % "0.7.26" % Test,
    testFrameworks += new TestFramework("munit.Framework")
)

lazy val root = project
  .in(file("."))
  .aggregate(gopher.js, gopher.jvm)
  .settings(
    git.remoteRepo := "git@github.com:rssh/scala-gopher.git",
    SiteScaladocPlugin.scaladocSettings(GopherJVM, gopher.jvm / Compile / packageDoc / mappings, "api/jvm"),
    SiteScaladocPlugin.scaladocSettings(GopherJS,  gopher.js / Compile / packageDoc / mappings, "api/js"),
    siteDirectory :=  baseDirectory.value / "target" / "site",
    publishArtifact := false,
  ).enablePlugins(GhpagesPlugin, SiteScaladocPlugin)
  


lazy val gopher = crossProject(JSPlatform, JVMPlatform)
    .in(file("."))
    .settings(sharedSettings)
    .disablePlugins(SitePlugin)
    .disablePlugins(SitePreviewPlugin)
    .jvmSettings(
        scalacOptions ++= Seq( "-unchecked", "-Ycheck:macros", "-uniqid", "-Xprint:types" ),
    ).jsSettings(
        libraryDependencies += ("org.scala-js" %%% "scalajs-java-logging" % "1.0.0").cross(CrossVersion.for3Use2_13),
        // TODO: switch to ModuleES ?
        scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
        scalaJSUseMainModuleInitializer := true,
    )

lazy val GopherJVM = config("gopher.jvm")
lazy val GopherJS = config("gopher.js")
