lazy val akkaVersion = "2.4.12"

val VERSION = "0.1.0"

val GROUP_ID = "net.petitviolet"

val PROJECT_NAME = "supervisor"

lazy val commonDependencies = Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.netflix.hystrix" % "hystrix-core" % "1.5.6",

  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",

  "org.scalatest" %% "scalatest" % "3.0.0" % "test",
  "org.mockito" % "mockito-core" % "1.10.19" % "test"
) ++ logDependencies

lazy val logDependencies = Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "ch.qos.logback" % "logback-core" % "1.1.7",
  "org.slf4j" % "slf4j-api" % "1.7.21"
)

def commonSettings(projectName: String) = Seq(
  name := projectName,
  scalaVersion := "2.12.0",
  version := VERSION,
  libraryDependencies := commonDependencies,
  organization := GROUP_ID
)

lazy val root = (project in file("."))
  .settings(name := "circuit-breaker-prac")
  .aggregate(example, supervisor)

lazy val example = (project in file("example"))
  .settings(commonSettings("example"))
  .settings(libraryDependencies += GROUP_ID %% PROJECT_NAME % VERSION)
//  .dependsOn(supervisor)

lazy val supervisor = (project in file(PROJECT_NAME))
  .settings(commonSettings(PROJECT_NAME))
  .settings(fork in Test := false)

