logLevel := Level.Warn

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += Resolver.url(
  "bintray-sbt-plugin-releases",
  url("http://dl.bintray.com/content/sbt/sbt-plugin-releases"))(
  Resolver.ivyStylePatterns)

// Formatter plugins
addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")

// Assembly
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.2")

resolvers += Classpaths.sbtPluginReleases

