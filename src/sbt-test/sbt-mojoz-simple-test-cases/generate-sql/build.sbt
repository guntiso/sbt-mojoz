
name := "sbt-mojoz-test"

organization := "org.mojoz"

version := "0.1"

scalaVersion := "2.12.12"

lazy val root = (project in file(".")).enablePlugins(MojozPlugin, MojozGenerateSchemaPlugin)

mojozMdConventions := org.mojoz.metadata.io.MdConventions

mojozDtosImports := Seq("sbtmojoz.test._")

mojozSchemaSqlFile := file("db/creation/schema.sql")

mojozSchemaSqlWriter := org.mojoz.metadata.out.SqlWriter.postgresql(typeDefs = mojozTypeDefs.value)