import sbt.Keys.organization

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-mojoz",
    organization := "org.mojoz",
    version := "2.1.1",
    scalaVersion := "2.12.14",
    ThisBuild / sbt.Keys.versionScheme := Some("semver-spec"),
    ThisBuild / versionPolicyIntention := Compatibility.BinaryAndSourceCompatible,
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
    resolvers += "snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    libraryDependencies ++= Seq(
      "org.mojoz"                  %% "mojoz"                             % "2.0.0",
      "org.mojoz"                  %% "querease"                          % "5.0.1",
      "org.tresql"                 %% "tresql"                            % "10.0.0"
    ),
    scriptedLaunchOpts := { scriptedLaunchOpts.value ++
      Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false
  )
  .settings(
    publishTo := version { v: String =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    }.value,
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
