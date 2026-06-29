import sbtcompat.PluginCompat._

name := "sbt-mojoz-test"

organization := "org.mojoz"

version := "0.1"

scalaVersion := "2.12.21"
exportJars := false

resolvers += "snapshots" at "https://central.sonatype.com/repository/maven-snapshots"

lazy val root = (project in file(".")).enablePlugins(MojozTableMetadataPlugin)

mojozMdConventions := Def.uncached(org.mojoz.metadata.io.MdConventions)

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8")

Compile / compile := Def.uncached {
  (Compile / copyResources).value // expose tresql-scala-macro.properties
  (Compile / compile).value
}

libraryDependencies ++= Seq(
  "org.mojoz"                  %% "querease"                          % "10.1.0-SNAPSHOT",
)
