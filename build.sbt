lazy val root = (project in file(".")).
  settings(
    name := "cpm-server",
    version := "0.1",
    scalaVersion := "2.11.7"
  )

val buildSettings = Defaults.coreDefaultSettings ++ Seq(
  //…
  javaOptions += "-Xmx3G"
  //…
)


/*libraryDependencies +=
  "com.typesafe.akka" %% "akka-actor" % "2.3.13"

resolvers += "Sonatype (releases)" at "https://oss.sonatype.org/content/repositories/releases/"
*/

//libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test"

libraryDependencies += "org.yaml" % "snakeyaml" % "1.16"

libraryDependencies += "org.zeromq" % "jeromq" % "0.3.5"

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"

libraryDependencies += "org.mongodb" %% "casbah" % "2.8.2"

libraryDependencies += "org.json4s" %% "json4s-native" % "3.3.0"

libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.3.0"

/*libraryDependencies += "org.spark-project.zeromq" % "zeromq-scala-binding_2.11" % "0.0.7-spark"*/
/*
 libraryDependencies ++= Seq(
    "net.java.dev.jna" %  "jna"           % "3.0.9",
      "com.github.jnr"   %  "jnr-constants" % "0.8.2",
        "org.scalatest"    %  "scalatest_2.10"     % "2.0.M5b" % "test"
      )
*/
//libraryDependencies += "org.zeromq" % "zeromq-scala-binding_2.11.0-M3" % "0.0.7"



/***
enablePlugins(ScalaJSPlugin)

libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "0.8.0"
*/
