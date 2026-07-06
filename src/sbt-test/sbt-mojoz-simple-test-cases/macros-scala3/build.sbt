import sbtcompat.PluginCompat._

name := "sbt-mojoz-test"

organization := "org.mojoz"

version := "0.1"

scalaVersion := "3.8.4"
exportJars := false

lazy val root = (project in file(".")).enablePlugins(MojozPlugin, MojozGenerateSchemaPlugin)

resolvers += "snapshots" at "https://central.sonatype.com/repository/maven-snapshots"

libraryDependencies ++= Seq(
  ("org.mojoz"               %% "querease"                   % "10.1.0").exclude(
  "org.scala-lang.modules",     "scala-parser-combinators_2.12"), // version conflict fix for plugin
  ("org.tresql"              %% "tresql"                     % "13.5.1").exclude(
  "org.scala-lang.modules",     "scala-parser-combinators_2.12"), // version conflict fix for plugin
  "org.scala-lang.modules"  %%  "scala-parser-combinators"  % "2.4.0" % "provided",
)

mojozMdConventions := Def.uncached(org.mojoz.metadata.io.MdConventions)

mojozDtosImports := Seq("sbtmojoz.test._")

mojozSchemaSqlDirectory := file("db/creation")

mojozShowFailedViewQuery := true
