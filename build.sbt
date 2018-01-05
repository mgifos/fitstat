name := "fitstat"

version := "1.0"

scalaVersion := "2.12.1"

val playVersion = "2.6.7"

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % "1.0.6",
  "com.typesafe.akka" %% "akka-stream" % "2.4.16",
  "com.typesafe.play" %% "play-json" % playVersion
)
    
