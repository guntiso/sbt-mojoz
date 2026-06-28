import sbt.Keys.organization

def scala212 = "2.12.21"
def scala3   = "3.8.4"

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-mojoz",
    organization := "org.mojoz",
    crossScalaVersions := Seq(scala212, scala3),
    scalaVersion := scala212,
    addSbtPlugin("com.github.sbt" % "sbt2-compat" % "0.1.0"),
    (pluginCrossBuild / sbtVersion) := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.12.12"
        case _      => "2.0.0"
      }
    },
    scripted / scalaVersion := scala212,
    scriptedSbt := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.12.12"
        case _      => "2.0.0"
      }
    },
    ThisBuild / sbt.Keys.versionScheme := Some("semver-spec"),
    ThisBuild / versionPolicyIntention := Compatibility.BinaryAndSourceCompatible,
    pluginCrossBuild / javacOptions ++= {
      scalaBinaryVersion.value match {
        case "2.12" => Seq("-release",  "8", "-Xlint")
        case _      => Seq("-release", "17", "-Xlint")
      }
    },
    initialize := {
      val _ = initialize.value
      val javaVersion = sys.props("java.specification.version").toDouble
      if (javaVersion != 17)
        sys.error("Java 17 is required to build this project. Found " + javaVersion + " instead")
    },
    resolvers += "snapshots" at "https://central.sonatype.com/repository/maven-snapshots",
    libraryDependencies ++= Seq(
      "org.mojoz"               %% "mojoz"                      % "7.1.1",
      "org.mojoz"               %% "querease"                   % "10.1.0-SNAPSHOT"  exclude(
      "org.scala-lang.modules",     "scala-parser-combinators_2.12"), // version conflict fix for plugin
      "org.tresql"              %% "tresql"                     % "13.4.0" exclude(
      "org.scala-lang.modules",     "scala-parser-combinators_2.12"), // version conflict fix for plugin
      "org.scala-lang.modules"  %%  "scala-parser-combinators"  % "2.4.0" % "provided",
    ),
    scriptedLaunchOpts := { scriptedLaunchOpts.value ++
      Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false
  )
  .settings(
    publishTo := {
      val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
      if (isSnapshot.value)
        Some("central-snapshots" at centralSnapshots)
      else
        localStaging.value
    },
    publishMavenStyle := true,
  )
  .settings(
    pomIncludeRepository := { _ => false },
    pomExtra :=
      <url>https://github.com/guntiso/sbt-mojoz</url>
      <licenses>
        <license>
          <name>MIT</name>
          <url>http://www.opensource.org/licenses/MIT</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:guntiso/sbt-mojoz.git</url>
        <connection>scm:git:git@github.com:guntiso/sbt-mojoz.git</connection>
      </scm>
      <developers>
        <developer>
          <id>muntis</id>
          <name>Muntis Grube</name>
          <url>https://github.com/muntis/</url>
        </developer>
        <developer>
          <id>guntiso</id>
          <name>Guntis Ozols</name>
          <url>https://github.com/guntiso/</url>
        </developer>
        <developer>
          <id>mrumkovskis</id>
          <name>Martins Rumkovskis</name>
          <url>https://github.com/mrumkovskis/</url>
        </developer>
      </developers>
  )
  .settings(
    Compile / scalacOptions := {
      val common = Seq("-unchecked", "-deprecation", "-feature", "-Wconf:cat=unused-nowarn:s")
      scalaBinaryVersion.value match {
        case "2.12" => common ++ Seq("-Xsource:3", "-release", "8")
        case _      => common ++ Seq("-java-output-version",  "17")
      }
    },
  )
