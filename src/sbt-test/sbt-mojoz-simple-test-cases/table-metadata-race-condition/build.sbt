import sbtcompat.PluginCompat._


lazy val commonSettings = Seq(
  organization := "org.mojoz",
  version := "0.1",
  scalaVersion := "2.12.21",
  exportJars := false,
  mojozMdConventions := Def.uncached(org.mojoz.metadata.io.MdConventions),
  mojozDtosImports := Seq("sbtmojoz.test._"),
  mojozScalaGenerator := Def.uncached(new org.mojoz.querease.ScalaDtoGenerator(mojozQuerease.value) {
    override def scalaClassName(name: String): String =
      name.split("[_\\-\\.]+").toList.map(_.toLowerCase.capitalize).mkString
  }),
)

lazy val common = (project in file("common"))
  .settings(
    scalaVersion := "2.12.21",
    exportJars := false,
  )

lazy val foo = (project in file("foo"))
  .dependsOn(common)
  .enablePlugins(MojozPlugin)
  .settings(commonSettings*)

lazy val bar = (project in file("bar"))
  .dependsOn(common)
  .enablePlugins(MojozPlugin)
  .settings(commonSettings*)

lazy val baz = (project in file("baz"))
  .dependsOn(common)
  .enablePlugins(MojozPlugin)
  .settings(commonSettings*)


val tablecount = 100
val joincount = 10


TaskKey[Unit]("buildsource") := Def.uncached {
  def writeFiles(project: String) = {
    import java.io._
    val pw = new PrintWriter(new File(s"$project/tables/$project.yaml" ))
    (0 until tablecount).foreach { i =>
      pw.write(
        s"""
           |table: $project$i
           |columns:
           |- id
           |- name
           |
      """.stripMargin)
    }
    pw.close

    import java.io._
    val pw2 = new PrintWriter(new File(s"$project/views/$project.yaml" ))
    (0 until tablecount).foreach { i =>
     pw2.write(
      s"""
         |name:    $project$i
         |table:   $project$i ttt
         |api: sistema_lietot list
         |joins:
         |""".stripMargin)
     
        (0 until joincount).foreach { ii =>
          pw2.write(s"- ttt[ttt.id = t$ii.id] $project$ii t$ii\n")
        }

     pw2.write(
      s"""fields:
         |- ttt.id
         |- ttt.name
         |order:
         |- ttt.id
         |
      """.stripMargin)
      }
    pw2.close
  }

  writeFiles("foo")
  writeFiles("bar")
  writeFiles("baz")
  ()
}
