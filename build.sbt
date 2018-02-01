name := "fitstat"

version := "1.0"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.lightbend.akka" %% "akka-stream-alpakka-file" % "0.15.1",
  "com.typesafe.akka" %% "akka-stream" % "2.5.8",
  "com.typesafe.akka" %% "akka-http"   % "10.0.11",
  "com.typesafe.play" %% "play-json" % "2.6.8",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2",
  guice,
  "org.scala-lang.modules" %% "scala-xml" % "1.0.6"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)

disablePlugins(PlayLayoutPlugin)
    
