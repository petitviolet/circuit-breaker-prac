// Your profile name of the sonatype account. The default is the same with the organization value
sonatypeProfileName := "net.petitviolet"

// To sync with Maven central, you need to supply the following information:
pomExtra in Global := {
  <url>https://github.com/petitviolet/supervisor</url>
  <licenses>
    <license>
      <name>Apache 2</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
    </license>
  </licenses>
  <scm>
    <connection>scm:git:github.com/petitviolet/supervisor</connection>
    <developerConnection>scm:git:git@github.com:petitviolet/supervisor</developerConnection>
    <url>github.com/petitviolet/supervisor</url>
  </scm>
  <developers>
    <developer>
      <id>net.petitviolet</id>
      <name>petitviolet</name>
      <url>https://www.petitviolet.net</url>
    </developer>
  </developers>
}
