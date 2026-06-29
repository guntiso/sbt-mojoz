import sbtcompat.PluginCompat._


lazy val commonSettings = Seq(
  organization := "org.mojoz",
  version := "0.1",
  scalaVersion := "2.12.21",
  exportJars := false,
  mojozMdConventions := Def.uncached(org.mojoz.metadata.io.MdConventions),
  mojozTypeDefs := Def.uncached(org.mojoz.metadata.TypeMetadata.customizedTypeDefs),
)

lazy val commonSchemaSettings = Seq(
  mojozSchemaSqlFiles := Def.uncached(Seq(file("db/schema.sql"))),
  mojozSchemaSqlGenerators := Def.uncached(Seq(org.mojoz.metadata.out.DdlGenerator.postgresql(typeDefs = mojozTypeDefs.value)))
)


lazy val parent = (project in file("parent"))
  .settings(
    name := "sbt-mojoz-test-parent"
  )
  .enablePlugins(MojozGenerateSchemaPlugin)
  .settings(commonSettings*)
  .settings(commonSchemaSettings*)

lazy val empty = (project in file("empty"))
  .settings(
    name := "sbt-mojoz-test-empty"
  )
  .settings(commonSettings*)

lazy val child = (project in file("child"))
  .dependsOn(parent, empty)
  .settings(
    name := "sbt-mojoz-test-child"
  )
  .enablePlugins(MojozGenerateSchemaPlugin)
  .settings(commonSettings*)
  .settings(commonSchemaSettings*)

TaskKey[Unit]("check") := Def.uncached {
  val lines = scala.io.Source.fromFile("db/schema.sql").mkString
  if (!lines.contains("create table child")) sys.error("Race condition met, db/schema.sql is generated from parent not child: \n"+lines)
  ()
}