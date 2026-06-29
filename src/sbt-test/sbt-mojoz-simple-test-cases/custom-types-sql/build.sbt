import sbtcompat.PluginCompat._


name := "sbt-mojoz-test"

organization := "org.mojoz"

version := "0.1"

scalaVersion := "2.12.21"
exportJars := false

lazy val root = (project in file(".")).enablePlugins(MojozPlugin, MojozGenerateSchemaPlugin)

mojozMdConventions := Def.uncached(org.mojoz.metadata.io.MdConventions)

mojozDtosImports := Seq("sbtmojoz.test._")

mojozSchemaSqlFiles := Def.uncached(Seq(file("db/creation/schema.sql")))

mojozSchemaSqlGenerators := Def.uncached(Seq(org.mojoz.metadata.out.DdlGenerator.postgresql(typeDefs = mojozTypeDefs.value)))
