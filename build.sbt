name := """circuit-breaker-prac"""

version := "1.0"

scalaVersion := "2.11.8"

val akkaVersion = "2.4.9-RC2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.netflix.hystrix" % "hystrix-core" % "1.5.6",
//  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",

  "org.scalatest" %% "scalatest" % "2.2.4" % "test")
