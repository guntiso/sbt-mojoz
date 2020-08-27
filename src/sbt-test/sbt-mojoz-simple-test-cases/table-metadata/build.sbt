name := "sbt-mojoz-test"

organization := "org.mojoz"

version := "0.1"

scalaVersion := "2.12.12"

resolvers += "snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

lazy val root = (project in file(".")).enablePlugins(MojozTableMetadataPlugin)

mojozMdConventions := mojoz.metadata.io.MdConventions

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8",
  "-Xmacro-settings:metadataFactoryClass=querease.TresqlMetadataFactory" +
    ", tableMetadataFile=" + mojozGenerateTresqlTableMetadata.value.getCanonicalPath)

libraryDependencies ++= Seq(
  "org.mojoz"                  %% "querease"                          % "4.0.0",
  "org.mojoz"                  %% "mojoz"                             % "1.2.1",
  "org.tresql"                 %% "tresql"                            % "10.0.0"
)
