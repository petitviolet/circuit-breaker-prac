lazy val akkaVersion = "2.4.12"

lazy val commonDependencies = Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.netflix.hystrix" % "hystrix-core" % "1.5.6",

//  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",

  "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test"
) ++ logDependencies

lazy val logDependencies = Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "ch.qos.logback" % "logback-core" % "1.1.7",
  "org.slf4j" % "slf4j-api" % "1.7.21"
)

def commonSettings(projectName: String) = Seq(
  name := projectName,
  scalaVersion := "2.12.0",
  version := "1.0",
  libraryDependencies := commonDependencies
)

lazy val root = (project in file("."))
  .settings(name := "circuit-breaker-prac")
  .aggregate(example, supervisor)

lazy val example = (project in file("example"))
  .settings(commonSettings("example"))
  .dependsOn(supervisor)

lazy val supervisor = (project in file("supervisor"))
  .settings(commonSettings("supervisor"))

