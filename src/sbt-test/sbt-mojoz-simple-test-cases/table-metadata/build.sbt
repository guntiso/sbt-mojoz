name := "sbt-mojoz-test"

organization := "org.mojoz"

version := "0.1"

scalaVersion := "2.12.19"

resolvers += "snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

lazy val root = (project in file(".")).enablePlugins(MojozTableMetadataPlugin)

mojozMdConventions := org.mojoz.metadata.io.MdConventions

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8")

Compile / compile := {
  (Compile / copyResources).value // expose tresql-scala-macro.properties
  (Compile / compile).value
}

libraryDependencies ++= Seq(
  "org.mojoz"                  %% "querease"                          % "7.0.0-RC9-SNAPSHOT",
)
