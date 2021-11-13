
name := "sbt-mojoz-test"

organization := "org.mojoz"

version := "0.1"

scalaVersion := "2.12.15"

lazy val root = (project in file(".")).enablePlugins(MojozPlugin, MojozGenerateSchemaPlugin)

mojozMdConventions := org.mojoz.metadata.io.MdConventions

mojozDtosImports := Seq("sbtmojoz.test._")

mojozSchemaSqlFile := file("db/creation/schema.sql")

mojozSchemaSqlGenerator := org.mojoz.metadata.out.SqlGenerator.postgresql(typeDefs = mojozTypeDefs.value)