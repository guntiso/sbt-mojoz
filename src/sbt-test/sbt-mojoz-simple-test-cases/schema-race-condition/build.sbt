
lazy val commonSettings = Seq(
  organization := "org.mojoz",
  version := "0.1",
  scalaVersion := "2.12.14",
  mojozMdConventions := org.mojoz.metadata.io.MdConventions,
  mojozTypeDefs := org.mojoz.metadata.TypeMetadata.customizedTypeDefs,
)

lazy val commonSchemaSettings = Seq(
  mojozSchemaSqlFile := file("db/schema.sql"),
  mojozSchemaSqlGenerator := org.mojoz.metadata.out.SqlGenerator.postgresql(typeDefs = mojozTypeDefs.value)
)


lazy val parent = (project in file("parent"))
  .settings(
    name := "sbt-mojoz-test-parent"
  )
  .enablePlugins(MojozGenerateSchemaPlugin)
  .settings(commonSettings: _*)
  .settings(commonSchemaSettings: _*)

lazy val empty = (project in file("empty"))
  .settings(
    name := "sbt-mojoz-test-empty"
  )
  .settings(commonSettings: _*)

lazy val child = (project in file("child"))
  .dependsOn(parent, empty)
  .settings(
    name := "sbt-mojoz-test-child"
  )
  .enablePlugins(MojozGenerateSchemaPlugin)
  .settings(commonSettings: _*)
  .settings(commonSchemaSettings: _*)

TaskKey[Unit]("check") := {
  val lines = scala.io.Source.fromFile("db/schema.sql").mkString
  if (!lines.contains("create table child")) sys.error("Race condition met, db/schema.sql is generated from parent not child: \n"+lines)
  ()
}