resolvers += "snapshots" at "https://central.sonatype.com/repository/maven-snapshots"

addSbtPlugin("com.github.sbt" % "sbt2-compat" % "0.1.0")

addSbtPlugin("org.mojoz" % "sbt-mojoz" % System.getProperty("plugin.version"))

