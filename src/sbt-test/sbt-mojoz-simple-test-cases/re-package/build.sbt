import sbtcompat.PluginCompat._


name := "sbt-mojoz-test"

organization := "org.mojoz"

version := "0.1"

scalaVersion := "2.12.21"
exportJars := false

lazy val root = (project in file(".")).enablePlugins(MojozPlugin)

mojozMdConventions := Def.uncached(org.mojoz.metadata.io.MdConventions)

mojozDtosPackage := "sample"

mojozDtosImports := Seq("sbtmojoz.test._")

mojozScalaGenerator := Def.uncached(new org.mojoz.querease.ScalaDtoGenerator(mojozQuerease.value) {
  override def scalaClassName(name: String): String =
    name.split("[_\\-\\.]+").toList.map(_.toLowerCase.capitalize).mkString
})
