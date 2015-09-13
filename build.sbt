organization := "woshilaiceshide"

name := "fetcher-proxy"

version := "1.3-SNAPSHOT"

compileOrder in Compile := CompileOrder.Mixed

transitiveClassifiers := Seq("sources")

EclipseKeys.withSource := true

scalaVersion := "2.11.7"

scalacOptions := Seq("-unchecked", "-deprecation","-optimise", "-encoding", "utf8", "-Yno-adapted-args")

javacOptions ++= Seq("-source", "1.7", "-target", "1.7")

//retrieveManaged := true

enablePlugins(JavaAppPackaging)

net.virtualvoid.sbt.graph.Plugin.graphSettings

unmanagedSourceDirectories in Compile <+= baseDirectory( _ / "src" / "java" )

unmanagedSourceDirectories in Compile <+= baseDirectory( _ / "src" / "scala" )

libraryDependencies ++= {
  val akkaV = "2.3.12"
  val sprayV = "1.3.3"
  Seq(
    "io.spray"            %%   "spray-routing" % sprayV,
    "io.spray"            %%   "spray-caching" % sprayV,
    "io.spray"            %%   "spray-can"     % sprayV,
    "io.spray"            %%   "spray-client"  % sprayV,
    "io.spray"            %%   "spray-http"    % sprayV,
    "io.spray"            %%   "spray-httpx"   % sprayV,
    "io.spray"            %%   "spray-io"      % sprayV,
  //"io.spray"            %%   "spray-testkit" % sprayV,
    "io.spray"            %%   "spray-util"    % sprayV,
    "io.spray"            %%   "spray-json"    % "1.3.2",
    "com.typesafe.akka"   %%  "akka-testkit"   % akkaV,
    "com.typesafe.akka"   %%  "akka-actor"     % akkaV,
    "com.typesafe.akka"   %%  "akka-slf4j"     % akkaV
  )
}

scriptClasspath := "../conf" +: scriptClasspath.value

mappings in Universal ++= (baseDirectory.value / "conf" * "*" get) map (x => x -> ("conf/" + x.getName))

mainClass in Compile := Some("woshilaiceshide.fetcher.Server")

libraryDependencies += "woshilaiceshide" %% "scala-web-repl" % "1.0-SNAPSHOT"

bashScriptExtraDefines += """addJava "-javaagent:${lib_dir}/woshilaiceshide.scala-web-repl_2.11-1.0-SNAPSHOT.jar""""
bashScriptExtraDefines += """addJava "-Dwrepl.listen.address=0.0.0.0""""
bashScriptExtraDefines += """addJava "-Dwrepl.listen.port=8585""""

