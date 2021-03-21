organization := "com.blackfynn"

name := "service-utilities"

scalaVersion := "2.12.11"

scalacOptions ++= Seq(
  "-language:postfixOps",
  "-language:implicitConversions",
  "-Xmax-classfile-name","100",
  "-feature",
  "-deprecation",
  "-Ypartial-unification"
)

publishTo := {
  val nexus = "https://nexus.pennsieve.cc/repository"

  if (isSnapshot.value) {
    Some("Nexus Realm" at s"$nexus/maven-snapshots")
  } else {
    Some("Nexus Realm" at s"$nexus/maven-releases")
  }
}

version := sys.props.get("version").getOrElse("SNAPSHOT")

scalafmtOnCompile := true

publishMavenStyle := true
publishArtifact in Test := true

resolvers ++= Seq(
  "Blackfynn Releases" at "https://nexus.pennsieve.cc/repository/maven-releases",
  "Blackfynn Snapshots" at "https://nexus.pennsieve.cc/repository/maven-snapshots",
  "Flyway" at "https://flywaydb.org/repo",
  Resolver.bintrayRepo("commercetools", "maven")
)

credentials += Credentials("Sonatype Nexus Repository Manager",
  "nexus.pennsieve.cc",
  sys.env("PENNSIEVE_NEXUS_USER"),
  sys.env("PENNSIEVE_NEXUS_PW")
)

lazy val AkkaHttpVersion = "10.1.11"
lazy val AkkaVersion     = "2.5.26"
lazy val CirceVersion    = "0.12.3"
lazy val LogbackVersion  = "1.2.3"

libraryDependencies ++= Seq(
  // --- DB --------------------------------------------------------------------------------------------------------
  "org.flywaydb"                % "flyway-core"              % "4.2.0",
  "org.postgresql"              % "postgresql"               % "42.1.4",
  // --- AKKA ------------------------------------------------------------------------------------------------------
  "com.typesafe.akka"          %% "akka-http"                % AkkaHttpVersion,
  "com.typesafe.akka"          %% "akka-stream"              % AkkaVersion,
  "com.typesafe.akka"          %% "akka-actor"               % AkkaVersion,
  // --- Logging ---------------------------------------------------------------------------------------------------
  "com.typesafe.akka"          %% "akka-slf4j"               % AkkaVersion,
  "com.typesafe.scala-logging" %% "scala-logging"            % "3.9.2",
  "ch.qos.logback"              % "logback-classic"          % LogbackVersion,
  "ch.qos.logback"              % "logback-core"             % LogbackVersion,
  "net.logstash.logback"        % "logstash-logback-encoder" % "5.2",
  // --- JSON (de)serialization ------------------------------------------------------------------------------------
  "de.heikoseeberger"          %% "akka-http-circe"          % "1.21.0",
  "io.circe"                   %% "circe-core"               % CirceVersion,
  "io.circe"                   %% "circe-generic"            % CirceVersion,
  "io.circe"                   %% "circe-parser"             % CirceVersion,
  // --- Testing ---------------------------------------------------------------------------------------------------
  "org.scalatest"              %% "scalatest"                % "3.0.1" % Test,
)
