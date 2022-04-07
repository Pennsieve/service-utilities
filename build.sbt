organization := "com.pennsieve"

name := "service-utilities"

lazy val scala212 = "2.12.11"
lazy val scala213 = "2.13.8"
lazy val supportedScalaVersions = List(scala212, scala213)

scalaVersion := scala212

crossScalaVersions := supportedScalaVersions

scalacOptions ++= Seq(
  "-language:postfixOps",
  "-language:implicitConversions",
  "-feature",
  "-deprecation",
)

scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
  case Some((2, 12)) =>
    Seq("-Xmax-classfile-name", "100", "-Ypartial-unification")
  case _ => Nil
})

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
Test / publishArtifact := true

resolvers ++= Seq(
  "Pennsieve Releases" at "https://nexus.pennsieve.cc/repository/maven-releases",
  "Pennsieve Snapshots" at "https://nexus.pennsieve.cc/repository/maven-snapshots",
  "Flyway" at "https://flywaydb.org/repo",
  Resolver.bintrayRepo("commercetools", "maven")
)

credentials += Credentials("Sonatype Nexus Repository Manager",
  "nexus.pennsieve.cc",
  sys.env("PENNSIEVE_NEXUS_USER"),
  sys.env("PENNSIEVE_NEXUS_PW")
)

lazy val AkkaHttpVersion = "10.1.11"
lazy val AkkaVersion     = "2.6.5"

lazy val circeVersion    = SettingKey[String]("circeVersion")
circeVersion := (CrossVersion.partialVersion(scalaVersion.value) match {
  case Some((2, 12)) => "0.11.1"
  case _ => "0.14.1"
})

lazy val LogbackVersion  = "1.2.3"

lazy val akkaHttpCirceVersion = SettingKey[String]("akkaHttpCirceVersion")
akkaHttpCirceVersion := (CrossVersion.partialVersion(scalaVersion.value) match {
  case Some((2, 12)) => "1.27.0"
  case _ => "1.39.2"
})

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
  "de.heikoseeberger"          %% "akka-http-circe"          % akkaHttpCirceVersion.value,
  "io.circe"                   %% "circe-core"               % circeVersion.value,
  "io.circe"                   %% "circe-generic"            % circeVersion.value,
  "io.circe"                   %% "circe-parser"             % circeVersion.value,
  // --- Testing ---------------------------------------------------------------------------------------------------
  "org.scalatest"              %% "scalatest"                % "3.2.11" % Test,
)
