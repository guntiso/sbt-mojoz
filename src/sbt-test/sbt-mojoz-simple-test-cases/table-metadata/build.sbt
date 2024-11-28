name := "sbt-mojoz-test"

organization := "org.mojoz"

version := "0.1"

scalaVersion := "2.12.19"

resolvers += "snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

lazy val root = (project in file(".")).enablePlugins(MojozTableMetadataPlugin)

mojozMdConventions := org.mojoz.metadata.io.MdConventions

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8",
  "-Xmacro-settings:metadataFactoryClass=org.mojoz.querease.TresqlMetadataFactory" +
    ", tableMetadataFile=" + mojozGenerateTresqlTableMetadata.value.getCanonicalPath)

libraryDependencies ++= Seq(
  "org.mojoz"                  %% "querease"                          % "6.3.2",
)
