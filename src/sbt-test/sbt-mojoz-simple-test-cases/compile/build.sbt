
name := "sbt-mojoz-test"

organization := "org.mojoz"

version := "0.1"

scalaVersion := "2.12.12"

lazy val root = (project in file(".")).enablePlugins(MojozPlugin)

mojozMdConventions := mojoz.metadata.io.MdConventions

mojozDtosImports := Seq("sbtmojoz.test._")

mojozScalaClassWriter := new querease.ScalaDtoGenerator(mojozQuerease.value) {
  override def scalaClassName(name: String): String = mojoz.metadata.Naming.camelize(name)
}
