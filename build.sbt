name := """circuit-breaker-prac"""

version := "1.0"

val akkaVersion = "2.4.9-RC2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.netflix.hystrix" %% "hystrix-core" % "4.0.0",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",

  "org.scalatest" %% "scalatest" % "2.2.4" % "test")
