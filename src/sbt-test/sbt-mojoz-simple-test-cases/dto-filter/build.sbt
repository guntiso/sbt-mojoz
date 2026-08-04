import sbtcompat.PluginCompat._


name := "sbt-mojoz-test"

organization := "org.mojoz"

version := "0.1"

scalaVersion := "2.12.21"
exportJars := false

lazy val root = (project in file(".")).enablePlugins(MojozPlugin)

mojozMdConventions := Def.uncached(org.mojoz.metadata.io.MdConventions)

mojozDtosImports := Seq("sbtmojoz.test._")

// Only generate DTOs for views with dto: true (and their transitive references)
mojozShouldGenerateDtoImplied := false
// Nested views without join metadata; DTO selection is what this test covers
mojozShouldCompileViews := false

mojozScalaGenerator := Def.uncached(new org.mojoz.querease.ScalaDtoGenerator(mojozQuerease.value) {
  override def scalaClassName(name: String): String =
    name.split("[_\\-\\.]+").toList.map(_.toLowerCase.capitalize).mkString
})
