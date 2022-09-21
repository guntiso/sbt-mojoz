
name := "sbt-mojoz-test"

organization := "org.mojoz"

version := "0.1"

scalaVersion := "2.12.17"

lazy val root = (project in file(".")).enablePlugins(MojozPlugin)

mojozMdConventions := org.mojoz.metadata.io.MdConventions

mojozDtosPackage := "sample"

mojozDtosImports := Seq("sbtmojoz.test._")

mojozScalaGenerator := new org.mojoz.querease.ScalaDtoGenerator(mojozQuerease.value) {
  override def scalaClassName(name: String): String =
    name.split("[_\\-\\.]+").toList.map(_.toLowerCase.capitalize).mkString
}
