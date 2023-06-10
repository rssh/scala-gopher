//val dottyVersion = "3.0.0-RC2-bin-SNAPSHOT"
val dottyVersion = "3.3.0"
//val dottyVersion = "3.1.3-RC1-bin-SNAPSHOT"
//val dottyVersion = dottyLatestNightlyBuild.get

ThisBuild/version := "3.0.8-SNAPSHOT"
ThisBuild/versionScheme := Some("semver-spec")

val sharedSettings = Seq(
    organization := "com.github.rssh",
    scalaVersion := dottyVersion,
    name := "scala-gopher",
    //resolvers += "Local Ivy Repository" at "file://"+Path.userHome.absolutePath+"/.ivy2/local",
    libraryDependencies += "com.github.rssh" %%% "dotty-cps-async" % "0.9.17-RC1",
    libraryDependencies += "org.scalameta" %%% "munit" % "0.7.29" % Test,
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
  

// for scala-native support we need munit 
lazy val gopher = crossProject(JSPlatform, JVMPlatform)
    .in(file("."))
    .settings(sharedSettings)
    .disablePlugins(SitePlugin)
    .disablePlugins(SitePreviewPlugin)
    .jvmSettings(
        //scalacOptions ++= Seq( "-unchecked", "-Xcheck-macros", "-Ycheck:macro", "-uniqid", "-Xprint:types", "-explain" ),
        // Error in dotty
        scalacOptions ++= Seq( "-unchecked", "-Xprint:types" ),
        fork := true,
        /*
        javaOptions ++= Seq(
         "--add-opens", 
         "java.base/java.lang=ALL-UNNAMED",
         s"-javaagent:${System.getProperty("user.home")}/.ivy2/local/com.github.rssh/trackedfuture_3/0.5.0/jars/trackedfuture_3-assembly.jar"
        )
        */
        Compile / doc / scalacOptions := Seq("-groups",
                "-source-links:shared=github://rssh/scala-gopher/master#shared",
                "-source-links:jvm=github://rssh/scala-gopher/master#jvm"),
        mimaPreviousArtifacts := Set( "com.github.rssh" %% "scala-gopher" % "3.0.5" )  
    ).jsSettings(
        libraryDependencies += ("org.scala-js" %%% "scalajs-java-logging" % "1.0.0").cross(CrossVersion.for3Use2_13),
        // TODO: switch to ModuleES ?
        scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
        scalaJSUseMainModuleInitializer := true,
        Compile / doc / scalacOptions := Seq("-groups",
            "-source-links:shared=github://rssh/scala-gopher/master#shared",
            "-source-links:js=github://rssh/scala-gopher/master#js"),
    )

lazy val GopherJVM = config("gopher.jvm")
lazy val GopherJS = config("gopher.js")
