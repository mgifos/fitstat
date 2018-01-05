name := "fitstat"

version := "1.0"

scalaVersion := "2.12.4"

val playVersion = "2.6.7"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.lightbend.akka" %% "akka-stream-alpakka-file" % "0.15.1",
  "com.typesafe.akka" %% "akka-stream" % "2.5.8",
  "com.typesafe.akka" %% "akka-http"   % "10.0.11",
  "com.typesafe.play" %% "play-json" % playVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.6"
)
    
